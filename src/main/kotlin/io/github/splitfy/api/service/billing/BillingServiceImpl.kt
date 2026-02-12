package io.github.splitfy.api.service.billing

import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.repository.SubscriberRepository
import io.github.splitfy.api.web.subscriber.dto.BillingItemDto
import io.github.splitfy.api.web.subscriber.dto.BillingResponse
import io.github.splitfy.api.web.subscriber.dto.Currency
import jakarta.persistence.EntityNotFoundException
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
            .orElseThrow { EntityNotFoundException("Subscriber not found with id: $subscriberId") }

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

        val items = associations.map { assoc ->
            val platform = assoc.platform
            val participantsCount = countsByPlatform[platform.id!!]?.getCount() ?: 0L

            if (participantsCount == 0L) {
                null // skip per rule
            } else {
                val price = platform.price
                val monthlyServiceAmount = when (platform.billingCycle) {
                    io.github.splitfy.api.domain.enums.BillingCycle.MONTHLY -> price
                    io.github.splitfy.api.domain.enums.BillingCycle.ANNUAL -> price.divide(BigDecimal(12), INTERMEDIATE_SCALE, ROUNDING)
                    else -> price // for SEMI_ANNUAL or others, default to full price (could be extended later)
                }

                val userShare = monthlyServiceAmount.divide(BigDecimal(participantsCount), INTERMEDIATE_SCALE, ROUNDING)
                    .setScale(FINAL_SCALE, ROUNDING)

                BillingItemDto(
                    serviceId = platform.id!!,
                    serviceName = platform.name,
                    billingCycle = platform.billingCycle,
                    serviceMonthlyAmount = monthlyServiceAmount.setScale(FINAL_SCALE, ROUNDING),
                    participantsCount = participantsCount.toInt(),
                    userMonthlyShare = userShare
                )
            }
        }.filterNotNull()

        val total = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.userMonthlyShare) }
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
