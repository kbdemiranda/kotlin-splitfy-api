package io.github.splitfy.api.service.billing

import io.github.splitfy.api.domain.enums.Currency as PlatformCurrency
import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.exception.ResourceNotFoundApiException
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.repository.SubscriberRepository
import io.github.splitfy.api.service.exchange.ExchangeRateQuote
import io.github.splitfy.api.service.exchange.ExchangeRateService
import io.github.splitfy.api.web.subscriber.dto.BillingItemDto
import io.github.splitfy.api.web.subscriber.dto.BillingResponse
import io.github.splitfy.api.web.subscriber.dto.Currency
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
    private val exchangeRateService: ExchangeRateService
) : BillingService {

    private val FINAL_SCALE = 2
    private val INTERMEDIATE_SCALE = 10
    private val ROUNDING = RoundingMode.HALF_UP

    override fun getBillingForSubscriber(subscriberId: Long, referenceMonth: YearMonth?): BillingResponse {
        val subscriber = subscriberRepository.findById(subscriberId)
            .orElseThrow { ResourceNotFoundApiException("Subscriber not found with id: $subscriberId") }

        val refMonth = referenceMonth ?: YearMonth.now()

        // load active associations with platforms (avoid N+1)
        val associations = subscriberPlatformRepository.findActiveBySubscriberIdWithPlatform(subscriberId)
        if (associations.isEmpty()) {
            return BillingResponse(
                userId = subscriberId,
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

        // build items plus a flag indicating if this service should be included in this month's total
        val itemsWithInclude = associations.mapNotNull { assoc ->
            val platform = assoc.platform
            val participantsCount = countsByPlatform[platform.id!!]?.getCount() ?: 0L

            if (participantsCount == 0L) {
                // skip from items/results per requirement
                null
            } else {
                val price = platform.price
                val exchangeQuote = if (platform.currency == PlatformCurrency.BRL) null else ratesByCurrency[platform.currency]
                val rateToBrl = exchangeQuote?.rateToBrl ?: BigDecimal.ONE
                val priceInBrl = price.multiply(rateToBrl)
                val serviceAmount = when (platform.billingCycle) {
                    io.github.splitfy.api.domain.enums.BillingCycle.MONTHLY -> priceInBrl
                    io.github.splitfy.api.domain.enums.BillingCycle.ANNUAL -> priceInBrl
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
                    exchangeRateDate = exchangeQuote?.quotedAt?.toLocalDate()
                )

                // Determine whether to include this service/userShare in the totalMonthlyDue
                val includeInTotal = when (platform.billingCycle) {
                    io.github.splitfy.api.domain.enums.BillingCycle.MONTHLY -> true
                    io.github.splitfy.api.domain.enums.BillingCycle.ANNUAL -> {
                        // include only if reference month matches platform billing month
                        val billingDate = platform.billingDate
                        billingDate != null && billingDate.monthValue == refMonth.monthValue
                    }
                    else -> false
                }

                Pair(item, includeInTotal)
            }
        }

        val items = itemsWithInclude.map { it.first }

        val total = itemsWithInclude
            .filter { it.second }
            .fold(BigDecimal.ZERO) { acc, pair -> acc.add(pair.first.userMonthlyShare) }
            .setScale(FINAL_SCALE, ROUNDING)

        return BillingResponse(
            userId = subscriberId,
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
}
