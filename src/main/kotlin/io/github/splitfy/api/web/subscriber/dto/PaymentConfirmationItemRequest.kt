package io.github.splitfy.api.web.subscriber.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Payment confirmation entry for one subscriber")
data class PaymentConfirmationItemRequest(
    @Schema(description = "Subscriber id", example = "1")
    val subscriberId: Long,
    @Schema(description = "List of platform/service ids paid by subscriber", example = "[1, 2]")
    val platformIds: List<Long>
)
