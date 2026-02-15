package io.github.splitfy.api.service.dashboard

import io.github.splitfy.api.domain.entity.SubscriberPlatform
import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.domain.enums.Currency
import io.github.splitfy.api.domain.enums.PaymentConfirmationStatus
import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.repository.PaymentConfirmationRepository
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.service.exchange.ExchangeRateQuote
import io.github.splitfy.api.service.exchange.ExchangeRateService
import io.github.splitfy.api.web.dashboard.dto.DashboardKpiResponse
import io.github.splitfy.api.web.dashboard.dto.PendingByPlatformItem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

@Service
@Transactional(readOnly = true)
class DashboardService(
    private val subscriberPlatformRepository: SubscriberPlatformRepository,
    private val paymentConfirmationRepository: PaymentConfirmationRepository,
    private val exchangeRateService: ExchangeRateService,
) {

    private val finalScale = 2
    private val intermediateScale = 10
    private val rounding = RoundingMode.HALF_UP

    fun getKpis(referenceMonth: YearMonth?): DashboardKpiResponse {
        val refMonth = referenceMonth ?: YearMonth.now()
        val activeAssociations = subscriberPlatformRepository.findAllActiveWithSubscriberAndPlatform()
        if (activeAssociations.isEmpty()) {
            return emptyResponse(refMonth)
        }

        val platformIds = activeAssociations.mapNotNull { it.platform.id }.distinct()
        if (platformIds.isEmpty()) {
            return emptyResponse(refMonth)
        }

        val participantsByPlatform = subscriberPlatformRepository
            .countActiveParticipantsByPlatformIds(platformIds)
            .associateBy { it.getPlatformId() }

        val ratesByCurrency = loadRatesForForeignCurrencies(
            activeAssociations.map { it.platform.currency }.distinct()
        )

        val dueShares = mutableMapOf<AssociationKey, DueShare>()
        activeAssociations.forEach { association ->
            val subscriberId = association.subscriber.id ?: return@forEach
            val platform = association.platform
            val platformId = platform.id ?: return@forEach
            val participants = participantsByPlatform[platformId]?.getCount() ?: 0L

            if (participants <= 0L) {
                return@forEach
            }

            val includeInMonth = shouldIncludeInMonth(platform.billingCycle, platform.billingDate?.monthValue, refMonth.monthValue)
            if (!includeInMonth) {
                return@forEach
            }

            val amountInBrl = calculateAmountInBrl(
                amount = platform.price,
                currency = platform.currency,
                ratesByCurrency = ratesByCurrency
            )
            val userShare = amountInBrl
                .divide(BigDecimal(participants), intermediateScale, rounding)
                .setScale(finalScale, rounding)

            dueShares[AssociationKey(subscriberId, platformId)] = DueShare(
                platformId = platformId,
                platformName = platform.name,
                userShare = userShare
            )
        }

        if (dueShares.isEmpty()) {
            return emptyResponse(refMonth)
        }

        val confirmationsByKey = paymentConfirmationRepository
            .findByReferenceMonthAndDeletedAtIsNull(refMonth)
            .associateBy { AssociationKey(it.subscriber.id!!, it.platform.id!!) }

        var totalDue = BigDecimal.ZERO
        var totalPaid = BigDecimal.ZERO
        var totalPending = BigDecimal.ZERO
        val pendingByPlatformAccumulator = mutableMapOf<Long, PendingAccumulator>()

        dueShares.forEach { (key, dueShare) ->
            totalDue = totalDue.add(dueShare.userShare)
            when (confirmationsByKey[key]?.status) {
                PaymentConfirmationStatus.CONFIRMED -> {
                    totalPaid = totalPaid.add(dueShare.userShare)
                }
                PaymentConfirmationStatus.PENDING -> {
                    totalPending = totalPending.add(dueShare.userShare)
                    val current = pendingByPlatformAccumulator[dueShare.platformId]
                    if (current == null) {
                        pendingByPlatformAccumulator[dueShare.platformId] = PendingAccumulator(
                            platformName = dueShare.platformName,
                            count = 1,
                            amount = dueShare.userShare
                        )
                    } else {
                        current.count += 1
                        current.amount = current.amount.add(dueShare.userShare)
                    }
                }
                else -> Unit
            }
        }

        val scaledTotalDue = totalDue.setScale(finalScale, rounding)
        val scaledTotalPaid = totalPaid.setScale(finalScale, rounding)
        val scaledTotalPending = totalPending.setScale(finalScale, rounding)
        val totalUnpaid = scaledTotalDue
            .subtract(scaledTotalPaid)
            .subtract(scaledTotalPending)
            .setScale(finalScale, rounding)

        val delinquencyRate = if (scaledTotalDue.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal.ZERO.setScale(finalScale, rounding)
        } else {
            totalUnpaid
                .divide(scaledTotalDue, intermediateScale, rounding)
                .multiply(BigDecimal("100"))
                .setScale(finalScale, rounding)
        }

        val pendingByPlatform = pendingByPlatformAccumulator.entries
            .map { (platformId, acc) ->
                PendingByPlatformItem(
                    platformId = platformId,
                    platformName = acc.platformName,
                    pendingCount = acc.count,
                    pendingAmount = acc.amount.setScale(finalScale, rounding)
                )
            }
            .sortedByDescending { it.pendingAmount }

        return DashboardKpiResponse(
            referenceMonth = refMonth,
            currency = Currency.BRL.name,
            totalDue = scaledTotalDue,
            totalPaid = scaledTotalPaid,
            totalPending = scaledTotalPending,
            totalUnpaid = totalUnpaid,
            delinquencyRate = delinquencyRate,
            pendingByPlatform = pendingByPlatform,
        )
    }

    private fun calculateAmountInBrl(
        amount: BigDecimal,
        currency: Currency,
        ratesByCurrency: Map<Currency, ExchangeRateQuote>,
    ): BigDecimal {
        val rateToBrl = if (currency == Currency.BRL) BigDecimal.ONE else ratesByCurrency[currency]?.rateToBrl
            ?: throw BadRequestApiException("Could not fetch BRL exchange rate for currency: ${currency.name}")
        return amount.multiply(rateToBrl).setScale(finalScale, rounding)
    }

    private fun loadRatesForForeignCurrencies(currencies: List<Currency>): Map<Currency, ExchangeRateQuote> {
        return currencies
            .filter { it != Currency.BRL }
            .associateWith { currency ->
                exchangeRateService.getLatestBrlRate(currency)
                    ?: throw BadRequestApiException("Could not fetch BRL exchange rate for currency: ${currency.name}")
            }
    }

    private fun shouldIncludeInMonth(cycle: BillingCycle, billingMonth: Int?, referenceMonth: Int): Boolean {
        return when (cycle) {
            BillingCycle.MONTHLY -> true
            BillingCycle.SEMI_ANNUAL -> false
            BillingCycle.ANNUAL -> billingMonth != null && billingMonth == referenceMonth
        }
    }

    private fun emptyResponse(referenceMonth: YearMonth): DashboardKpiResponse {
        val zero = BigDecimal.ZERO.setScale(finalScale, rounding)
        return DashboardKpiResponse(
            referenceMonth = referenceMonth,
            currency = Currency.BRL.name,
            totalDue = zero,
            totalPaid = zero,
            totalPending = zero,
            totalUnpaid = zero,
            delinquencyRate = zero,
            pendingByPlatform = emptyList(),
        )
    }

    private data class AssociationKey(
        val subscriberId: Long,
        val platformId: Long,
    )

    private data class DueShare(
        val platformId: Long,
        val platformName: String,
        val userShare: BigDecimal,
    )

    private data class PendingAccumulator(
        val platformName: String,
        var count: Int,
        var amount: BigDecimal,
    )
}
