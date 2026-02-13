package io.github.splitfy.api.service.billing

import io.github.splitfy.api.exception.ResourceNotFoundApiException
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.repository.SubscriberRepository
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
    private val subscriberPlatformRepository: SubscriberPlatformRepository
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

        // build items plus a flag indicating if this service should be included in this month's total
        val itemsWithInclude = associations.mapNotNull { assoc ->
            val platform = assoc.platform
            val participantsCount = countsByPlatform[platform.id!!]?.getCount() ?: 0L

            if (participantsCount == 0L) {
                // skip from items/results per requirement
                null
            } else {
                val price = platform.price
                val serviceAmount = when (platform.billingCycle) {
                    io.github.splitfy.api.domain.enums.BillingCycle.MONTHLY -> price
                    io.github.splitfy.api.domain.enums.BillingCycle.ANNUAL -> price
                    else -> price
                }

                val userShare = serviceAmount.divide(BigDecimal(participantsCount), INTERMEDIATE_SCALE, ROUNDING)
                    .setScale(FINAL_SCALE, ROUNDING)

                val item = BillingItemDto(
                    serviceId = platform.id!!,
                    serviceName = platform.name,
                    billingCycle = platform.billingCycle,
                    serviceMonthlyAmount = serviceAmount.setScale(FINAL_SCALE, ROUNDING),
                    participantsCount = participantsCount.toInt(),
                    userMonthlyShare = userShare
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
}
