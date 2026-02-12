package io.github.splitfy.api.web.subscriber.dto

import io.github.splitfy.api.domain.enums.BillingCycle
import java.math.BigDecimal
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Billing item for a service/platform")
data class BillingItemDto(
    val serviceId: Long,
    val serviceName: String,
    val billingCycle: BillingCycle,
    val serviceMonthlyAmount: BigDecimal,
    val participantsCount: Int,
    val userMonthlyShare: BigDecimal
)

