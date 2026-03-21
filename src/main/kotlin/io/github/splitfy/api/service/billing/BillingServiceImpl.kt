package io.github.splitfy.api.service.billing

import io.github.splitfy.api.domain.enums.Currency as PlatformCurrency
import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.domain.enums.PaymentConfirmationStatus
import io.github.splitfy.api.domain.entity.PaymentConfirmation
import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.exception.ResourceNotFoundApiException
import io.github.splitfy.api.repository.PaymentConfirmationRepository
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.repository.SubscriberRepository
import io.github.splitfy.api.service.exchange.ExchangeRateQuote
import io.github.splitfy.api.service.exchange.ExchangeRateService
import io.github.splitfy.api.web.billing.dto.BillingCoveredSubscriberItemDto
import io.github.splitfy.api.web.billing.dto.BillingItemDto
import io.github.splitfy.api.web.billing.dto.BillingResponse
import io.github.splitfy.api.web.billing.dto.Currency
import io.github.splitfy.api.web.billing.dto.PaymentStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

@Service
@Transactional(readOnly = true)
class BillingServiceImpl(
    private val subscriberRepository: SubscriberRepository,
    private val subscriberPlatformRepository: SubscriberPlatformRepository,
    private val paymentConfirmationRepository: PaymentConfirmationRepository,
    private val exchangeRateService: ExchangeRateService
) : BillingService {

    private val FINAL_SCALE = 2
    private val INTERMEDIATE_SCALE = 10
    private val ROUNDING = RoundingMode.HALF_UP

    override fun getBillingForSubscriber(subscriberId: Long, referenceMonth: YearMonth?): BillingResponse {
        val subscriber = subscriberRepository.findById(subscriberId)
            .orElseThrow { ResourceNotFoundApiException("Subscriber not found with id: $subscriberId") }

        val refMonth = referenceMonth ?: YearMonth.now()
        val coveredSubscribers = subscriberRepository
            .findByFinancialResponsibleSubscriberIdAndDeletedAtIsNull(subscriberId)
            .filter { it.id != subscriberId }
        val billedSubscribers = listOf(subscriber) + coveredSubscribers
        val billedSubscriberIds = billedSubscribers.mapNotNull { it.id }.distinct()
        val confirmationsByKey = paymentConfirmationRepository
            .findBySubscriberIdInAndReferenceMonthAndDeletedAtIsNull(billedSubscriberIds, refMonth)
            .associateBy { ConfirmationKey(it.subscriber.id!!, it.platform.id!!) }

        val associations = billedSubscriberIds
            .flatMap { billedSubscriberId -> subscriberPlatformRepository.findActiveBySubscriberIdWithPlatform(billedSubscriberId) }
        if (associations.isEmpty()) {
            return BillingResponse(
                userId = subscriberId,
                name = subscriber.name,
                email = subscriber.email,
                referenceMonth = refMonth,
                currency = Currency.BRL,
                items = emptyList<BillingItemDto>(),
                totalMonthlyDue = BigDecimal.ZERO.setScale(FINAL_SCALE, ROUNDING)
            )
        }

        val platformIds = associations.map { it.platform.id!! }.distinct()
        val counts = subscriberPlatformRepository.countActiveParticipantsByPlatformIds(platformIds)
        val countsByPlatform = counts.associateBy { it.getPlatformId() }
        val ratesByCurrency = loadRatesForForeignCurrencies(associations.map { it.platform.currency }.distinct())

        val itemsWithInclude = associations
            .groupBy { it.platform.id!! }
            .values
            .mapNotNull { platformAssociations ->
                val platform = platformAssociations.first().platform
            val participantsCount = countsByPlatform[platform.id!!]?.getCount() ?: 0L

            if (participantsCount == 0L) {
                null
            } else {
                val includeInMonth = shouldIncludeInMonth(
                    cycle = platform.billingCycle,
                    billingMonth = platform.billingDate?.monthValue,
                    referenceMonth = refMonth.monthValue
                )
                if (!includeInMonth) {
                    return@mapNotNull null
                }

                val price = platform.price
                val exchangeQuote = if (platform.currency == PlatformCurrency.BRL) null else ratesByCurrency[platform.currency]
                val rateToBrl = exchangeQuote?.rateToBrl ?: BigDecimal.ONE
                val priceInBrl = price.multiply(rateToBrl)
                val serviceAmount = when (platform.billingCycle) {
                    BillingCycle.MONTHLY -> priceInBrl
                    BillingCycle.ANNUAL -> priceInBrl
                    else -> priceInBrl
                }.setScale(FINAL_SCALE, ROUNDING)

                val userShare = serviceAmount.divide(BigDecimal(participantsCount), INTERMEDIATE_SCALE, ROUNDING)
                    .setScale(FINAL_SCALE, ROUNDING)
                val userShareOriginal = if (platform.currency == PlatformCurrency.BRL) {
                    null
                } else {
                    price.divide(BigDecimal(participantsCount), INTERMEDIATE_SCALE, ROUNDING)
                        .setScale(FINAL_SCALE, ROUNDING)
                }

                val coveredSubscriberItems = platformAssociations.map { assoc ->
                    val status = toPaymentStatus(
                        confirmationsByKey[ConfirmationKey(assoc.subscriber.id!!, platform.id!!)]
                    )
                    BillingCoveredSubscriberItemDto(
                        subscriberId = assoc.subscriber.id!!,
                        subscriberName = assoc.subscriber.name,
                        monthlyShare = userShare,
                        monthlyShareOriginal = userShareOriginal,
                        paymentStatus = status
                    )
                }
                val aggregatedStatus = aggregatePaymentStatus(coveredSubscriberItems.map { it.paymentStatus })
                val aggregatedShare = coveredSubscriberItems.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.monthlyShare) }
                    .setScale(FINAL_SCALE, ROUNDING)
                val aggregatedShareOriginal = if (userShareOriginal == null) {
                    null
                } else {
                    coveredSubscriberItems.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.monthlyShareOriginal ?: BigDecimal.ZERO) }
                        .setScale(FINAL_SCALE, ROUNDING)
                }

                val item = BillingItemDto(
                    serviceId = platform.id!!,
                    serviceName = platform.name,
                    billingCycle = platform.billingCycle,
                    serviceCurrency = platform.currency.name,
                    serviceMonthlyAmount = serviceAmount,
                    participantsCount = participantsCount.toInt(),
                    userMonthlyShare = aggregatedShare,
                    serviceMonthlyAmountOriginal = if (platform.currency == PlatformCurrency.BRL) null else price.setScale(FINAL_SCALE, ROUNDING),
                    userMonthlyShareOriginal = aggregatedShareOriginal,
                    exchangeRateToBrl = exchangeQuote?.rateToBrl,
                    exchangeRateDate = exchangeQuote?.quotedAt?.toLocalDate(),
                    paymentStatus = aggregatedStatus,
                    coveredSubscribers = coveredSubscriberItems
                )

                Pair(item, true)
            }
        }

        val items = itemsWithInclude.map { it.first }

        val total = itemsWithInclude
            .filter { it.second }
            .fold(BigDecimal.ZERO) { acc, pair -> acc.add(pair.first.userMonthlyShare) }
            .setScale(FINAL_SCALE, ROUNDING)

        return BillingResponse(
            userId = subscriberId,
            name = subscriber.name,
            email = subscriber.email,
            referenceMonth = refMonth,
            currency = Currency.BRL,
            items = items,
            totalMonthlyDue = total
        )
    }

    private fun loadRatesForForeignCurrencies(currencies: List<PlatformCurrency>): Map<PlatformCurrency, ExchangeRateQuote> {
        return currencies
            .filter { it != PlatformCurrency.BRL }
            .associateWith { currency ->
                exchangeRateService.getLatestBrlRate(currency)
                    ?: throw BadRequestApiException("Could not fetch BRL exchange rate for currency: ${currency.name}")
            }
    }

    private fun toPaymentStatus(confirmation: PaymentConfirmation?): PaymentStatus {
        if (confirmation == null) {
            return PaymentStatus.UNPAID
        }
        return when (confirmation.status) {
            PaymentConfirmationStatus.PENDING -> PaymentStatus.PENDING
            PaymentConfirmationStatus.CONFIRMED -> PaymentStatus.PAID
        }
    }

    private fun aggregatePaymentStatus(statuses: List<PaymentStatus>): PaymentStatus {
        if (statuses.isEmpty()) {
            return PaymentStatus.UNPAID
        }
        if (statuses.all { it == PaymentStatus.PAID }) {
            return PaymentStatus.PAID
        }
        if (statuses.any { it == PaymentStatus.PENDING }) {
            return PaymentStatus.PENDING
        }
        return PaymentStatus.UNPAID
    }

    private fun shouldIncludeInMonth(cycle: BillingCycle, billingMonth: Int?, referenceMonth: Int): Boolean {
        return when (cycle) {
            BillingCycle.MONTHLY -> true
            BillingCycle.SEMI_ANNUAL -> false
            BillingCycle.ANNUAL -> billingMonth != null && billingMonth == referenceMonth
        }
    }

    private data class ConfirmationKey(
        val subscriberId: Long,
        val platformId: Long
    )
}
