package io.github.splitfy.api.web.subscriber.dto

import io.github.splitfy.api.domain.enums.Currency
import io.github.splitfy.api.domain.enums.ServiceType
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime

@Schema(description = "Subscription item associated with subscriber")
data class SubscriptionItemResponse(
    @Schema(description = "Association ID")
    val id: Long?,

    @Schema(description = "Platform ID")
    val platformId: Long?,

    @Schema(description = "Platform name")
    val platformName: String,

    @Schema(description = "Platform price")
    val price: BigDecimal,

    @Schema(description = "Platform currency")
    val currency: Currency,

    @Schema(description = "Platform URL")
    val url: String?,

    @Schema(description = "Platform service type")
    val serviceType: ServiceType,

    @Schema(description = "Subscription creation date")
    val subscribedAt: LocalDateTime
)
