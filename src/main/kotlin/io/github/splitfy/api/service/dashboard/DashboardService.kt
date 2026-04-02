package io.github.splitfy.api.service.dashboard

import io.github.splitfy.api.domain.entity.SubscriberPlatform
import io.github.splitfy.api.domain.entity.PaymentConfirmation
import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.domain.enums.Currency
import io.github.splitfy.api.domain.enums.PaymentConfirmationStatus
import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.repository.PaymentConfirmationRepository
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.service.exchange.ExchangeRateQuote
import io.github.splitfy.api.service.exchange.ExchangeRateService
import io.github.splitfy.api.web.dashboard.dto.DebtorSubscriberItem
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
        val allActiveAssociations = subscriberPlatformRepository.findAllActiveWithSubscriberAndPlatform()
        if (allActiveAssociations.isEmpty()) {
            return emptyResponse(refMonth)
        }

        val confirmations = paymentConfirmationRepository
            .findByReferenceMonthAndDeletedAtIsNull(refMonth)
        val confirmationsByKey = confirmations
            .associateBy { AssociationKey(it.subscriber.id!!, it.platform.id!!) }

        val activeAssociations = filterAssociationsForReferenceMonth(
            associations = allActiveAssociations,
            referenceMonth = refMonth,
            confirmationsByKey = confirmationsByKey
        )
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
                subscriberId = subscriberId,
                subscriberName = association.subscriber.name,
                subscriberEmail = association.subscriber.email,
                platformId = platformId,
                platformName = platform.name,
                userShare = userShare
            )
        }

        if (dueShares.isEmpty()) {
            return emptyResponse(refMonth)
        }

        val confirmationsByEmailAndPlatform = confirmations
            .groupBy { EmailPlatformKey(it.subscriber.email.trim().lowercase(), it.platform.id!!) }

        var totalDue = BigDecimal.ZERO
        var totalPaid = BigDecimal.ZERO
        var totalPending = BigDecimal.ZERO
        val pendingByPlatformAccumulator = mutableMapOf<Long, PendingAccumulator>()
        val debtorsAccumulator = mutableMapOf<Long, DebtorAccumulator>()

        dueShares.forEach { (key, dueShare) ->
            val confirmation = confirmationsByKey[key]
                ?: confirmationsByEmailAndPlatform[
                    EmailPlatformKey(
                        subscriberEmail = dueShare.subscriberEmail.trim().lowercase(),
                        platformId = dueShare.platformId
                    )
                ]?.firstOrNull { it.status == PaymentConfirmationStatus.PENDING }
            totalDue = totalDue.add(dueShare.userShare)
            when (confirmation?.status) {
                PaymentConfirmationStatus.CONFIRMED -> {
                    totalPaid = totalPaid.add(dueShare.userShare)
                }
                PaymentConfirmationStatus.PENDING -> registerPendingAmount(
                    totalPending = totalPending,
                    debtorsAccumulator = debtorsAccumulator,
                    pendingByPlatformAccumulator = pendingByPlatformAccumulator,
                    dueShare = dueShare
                ).also { totalPending = it }
                else -> {
                    totalPending = registerPendingAmount(
                        totalPending = totalPending,
                        debtorsAccumulator = debtorsAccumulator,
                        pendingByPlatformAccumulator = pendingByPlatformAccumulator,
                        dueShare = dueShare
                    )
                }
            }
        }

        val scaledTotalDue = totalDue.setScale(finalScale, rounding)
        val scaledTotalPaid = totalPaid.setScale(finalScale, rounding)
        val scaledTotalPending = totalPending.setScale(finalScale, rounding)
        val totalUnpaid = BigDecimal.ZERO.setScale(finalScale, rounding)

        val delinquencyRate = if (scaledTotalDue.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal.ZERO.setScale(finalScale, rounding)
        } else {
            scaledTotalPending
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

        val debtors = debtorsAccumulator.values
            .map { acc ->
                val pendingAmount = acc.pendingAmount.setScale(finalScale, rounding)
                val unpaidAmount = BigDecimal.ZERO.setScale(finalScale, rounding)
                DebtorSubscriberItem(
                    subscriberId = acc.subscriberId,
                    subscriberName = acc.subscriberName,
                    subscriberEmail = acc.subscriberEmail,
                    pendingAmount = pendingAmount,
                    unpaidAmount = unpaidAmount,
                    totalDebt = pendingAmount.add(unpaidAmount).setScale(finalScale, rounding)
                )
            }
            .sortedWith(
                compareByDescending<DebtorSubscriberItem> { it.totalDebt }
                    .thenBy { it.subscriberName }
            )

        return DashboardKpiResponse(
            referenceMonth = refMonth,
            currency = Currency.BRL.name,
            totalDue = scaledTotalDue,
            totalPaid = scaledTotalPaid,
            totalPending = scaledTotalPending,
            totalUnpaid = totalUnpaid,
            delinquencyRate = delinquencyRate,
            pendingByPlatform = pendingByPlatform,
            debtors = debtors,
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

    private fun filterAssociationsForReferenceMonth(
        associations: List<SubscriberPlatform>,
        referenceMonth: YearMonth,
        confirmationsByKey: Map<AssociationKey, PaymentConfirmation>
    ): List<SubscriberPlatform> {
        val settledSubscribers = settledSubscribersInReferenceMonth(
            associations = associations,
            referenceMonth = referenceMonth,
            confirmationsByKey = confirmationsByKey
        )

        return associations.filter { association ->
            if (!isActiveInReferenceMonth(association, referenceMonth)) {
                return@filter false
            }
            if (!shouldIncludeInMonth(
                    cycle = association.platform.billingCycle,
                    billingMonth = association.platform.billingDate?.monthValue,
                    referenceMonth = referenceMonth.monthValue
                )
            ) {
                return@filter false
            }

            val associationMonth = YearMonth.from(association.subscribedAt)
            when {
                associationMonth.isBefore(referenceMonth) -> true
                associationMonth.isAfter(referenceMonth) -> false
                else -> !(settledSubscribers[association.subscriber.id!!] ?: false)
            }
        }
    }

    private fun settledSubscribersInReferenceMonth(
        associations: List<SubscriberPlatform>,
        referenceMonth: YearMonth,
        confirmationsByKey: Map<AssociationKey, PaymentConfirmation>
    ): Map<Long, Boolean> {
        return associations.groupBy { it.subscriber.id!! }
            .mapValues { (subscriberId, subscriberAssociations) ->
                val previousMonthAssociations = subscriberAssociations.filter { association ->
                    YearMonth.from(association.subscribedAt).isBefore(referenceMonth) &&
                        isActiveInReferenceMonth(association, referenceMonth) &&
                        shouldIncludeInMonth(
                            cycle = association.platform.billingCycle,
                            billingMonth = association.platform.billingDate?.monthValue,
                            referenceMonth = referenceMonth.monthValue
                        )
                }

                previousMonthAssociations.isNotEmpty() && previousMonthAssociations.all { association ->
                    confirmationsByKey[AssociationKey(subscriberId, association.platform.id!!)]?.status ==
                        PaymentConfirmationStatus.CONFIRMED
                }
            }
    }

    private fun isActiveInReferenceMonth(association: SubscriberPlatform, referenceMonth: YearMonth): Boolean {
        val monthStart = referenceMonth.atDay(1).atStartOfDay()
        val monthEndExclusive = referenceMonth.plusMonths(1).atDay(1).atStartOfDay()
        val activeUntil = association.unsubscribedAt
        return association.subscribedAt.isBefore(monthEndExclusive) &&
            (activeUntil == null || activeUntil.isAfter(monthStart))
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
            debtors = emptyList(),
        )
    }

    private fun accumulateDebtor(
        debtorsAccumulator: MutableMap<Long, DebtorAccumulator>,
        dueShare: DueShare,
        pendingIncrement: BigDecimal,
        unpaidIncrement: BigDecimal,
    ) {
        val current = debtorsAccumulator[dueShare.subscriberId]
        if (current == null) {
            debtorsAccumulator[dueShare.subscriberId] = DebtorAccumulator(
                subscriberId = dueShare.subscriberId,
                subscriberName = dueShare.subscriberName,
                subscriberEmail = dueShare.subscriberEmail,
                pendingAmount = pendingIncrement,
                unpaidAmount = unpaidIncrement
            )
        } else {
            current.pendingAmount = current.pendingAmount.add(pendingIncrement)
            current.unpaidAmount = current.unpaidAmount.add(unpaidIncrement)
        }
    }

    private fun registerPendingAmount(
        totalPending: BigDecimal,
        debtorsAccumulator: MutableMap<Long, DebtorAccumulator>,
        pendingByPlatformAccumulator: MutableMap<Long, PendingAccumulator>,
        dueShare: DueShare,
    ): BigDecimal {
        accumulateDebtor(
            debtorsAccumulator = debtorsAccumulator,
            dueShare = dueShare,
            pendingIncrement = dueShare.userShare,
            unpaidIncrement = BigDecimal.ZERO
        )
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
        return totalPending.add(dueShare.userShare)
    }

    private data class AssociationKey(
        val subscriberId: Long,
        val platformId: Long,
    )

    private data class EmailPlatformKey(
        val subscriberEmail: String,
        val platformId: Long,
    )

    private data class DueShare(
        val subscriberId: Long,
        val subscriberName: String,
        val subscriberEmail: String,
        val platformId: Long,
        val platformName: String,
        val userShare: BigDecimal,
    )

    private data class PendingAccumulator(
        val platformName: String,
        var count: Int,
        var amount: BigDecimal,
    )

    private data class DebtorAccumulator(
        val subscriberId: Long,
        val subscriberName: String,
        val subscriberEmail: String,
        var pendingAmount: BigDecimal,
        var unpaidAmount: BigDecimal,
    )
}
