package io.github.splitfy.api.web.platform.dto

import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.domain.enums.ServiceType
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.MonthDay

@Schema(description = "Platform data")
data class PlatformResponse(
    @Schema(description = "Platform ID")
    val id: Long?,

    @Schema(description = "Platform name", example = "Netflix")
    val name: String,

    @Schema(description = "Price", example = "19.90")
    val price: BigDecimal,

    @Schema(description = "URL", example = "https://www.netflix.com")
    val url: String? = null,

    @Schema(description = "Service type", example = "Video Streaming")
    val serviceType: ServiceType,

    @Schema(description = "Total slots", example = "4")
    val totalSlots: Int,

    @Schema(description = "Available slots", example = "2")
    val availableSlots: Int,

    @Schema(description = "Created at")
    val createdAt: LocalDateTime,

    @Schema(description = "Updated at")
    val updatedAt: LocalDateTime? = null,

    @Schema(description = "Deleted at")
    val deletedAt: LocalDateTime? = null,

    @Schema(description = "Billing cycle")
    val billingCycle: BillingCycle = BillingCycle.MONTHLY,

    @Schema(description = "Billing day (if applicable)")
    val billingDay: MonthDay? = null
)
