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
        val confirmationsByPlatformId = paymentConfirmationRepository
            .findBySubscriberIdAndReferenceMonthAndDeletedAtIsNull(subscriberId, refMonth)
            .associateBy { it.platform.id!! }

        // load active associations with platforms (avoid N+1)
        val associations = subscriberPlatformRepository.findActiveBySubscriberIdWithPlatform(subscriberId)
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

        val platformIds = associations.map { it.platform.id!! }
        val counts = subscriberPlatformRepository.countActiveParticipantsByPlatformIds(platformIds)
        val countsByPlatform = counts.associateBy { it.getPlatformId() }
        val ratesByCurrency = loadRatesForForeignCurrencies(associations.map { it.platform.currency }.distinct())

        // build only items that should be shown for the reference month
        val itemsWithInclude = associations.mapNotNull { assoc ->
            val platform = assoc.platform
            val participantsCount = countsByPlatform[platform.id!!]?.getCount() ?: 0L

            if (participantsCount == 0L) {
                // skip from items/results per requirement
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

                val item = BillingItemDto(
                    serviceId = platform.id!!,
                    serviceName = platform.name,
                    billingCycle = platform.billingCycle,
                    serviceCurrency = platform.currency.name,
                    serviceMonthlyAmount = serviceAmount,
                    participantsCount = participantsCount.toInt(),
                    userMonthlyShare = userShare,
                    serviceMonthlyAmountOriginal = if (platform.currency == PlatformCurrency.BRL) null else price.setScale(FINAL_SCALE, ROUNDING),
                    userMonthlyShareOriginal = userShareOriginal,
                    exchangeRateToBrl = exchangeQuote?.rateToBrl,
                    exchangeRateDate = exchangeQuote?.quotedAt?.toLocalDate(),
                    paymentStatus = toPaymentStatus(confirmationsByPlatformId[platform.id])
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

    private fun shouldIncludeInMonth(cycle: BillingCycle, billingMonth: Int?, referenceMonth: Int): Boolean {
        return when (cycle) {
            BillingCycle.MONTHLY -> true
            BillingCycle.SEMI_ANNUAL -> false
            BillingCycle.ANNUAL -> billingMonth != null && billingMonth == referenceMonth
        }
    }
}
