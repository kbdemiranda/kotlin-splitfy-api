package io.github.splitfy.api.web.subscriber.dto


import io.github.splitfy.api.domain.enums.ServiceType
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Subscription item associated with subscriber")
data class SubscriptionItemResponse(
    @Schema(description = "Platform ID")
    val platformId: Long?,

    @Schema(description = "Platform name")
    val platformName: String,

    @Schema(description = "Platform service type")
    val serviceType: ServiceType,
)
