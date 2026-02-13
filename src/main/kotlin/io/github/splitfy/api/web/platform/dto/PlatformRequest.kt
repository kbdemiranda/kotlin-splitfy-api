package io.github.splitfy.api.web.platform.dto

import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.domain.enums.Currency
import io.github.splitfy.api.domain.enums.ServiceType
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.MonthDay

@Schema(description = "Subscription platform input data")
data class PlatformRequest(
    @Schema(description = "Platform name", example = "Netflix")
    val name: String,

    @Schema(description = "Price", example = "19.90")
    val price: BigDecimal,

    @Schema(description = "Currency", example = "BRL", defaultValue = "BRL")
    val currency: Currency = Currency.BRL,

    @Schema(description = "URL", example = "https://www.netflix.com")
    val url: String? = null,

    @Schema(description = "Service type", example = "Video Streaming")
    val serviceType: ServiceType,

    @Schema(description = "Total slots", example = "4")
    val totalSlots: Int,

    @Schema(description = "Available slots", example = "2")
    val availableSlots: Int,

    @Schema(description = "Billing cycle")
    val billingCycle: BillingCycle = BillingCycle.MONTHLY,

    @Schema(description = "Billing day (if applicable)")
    val billingDay: MonthDay? = null
)
