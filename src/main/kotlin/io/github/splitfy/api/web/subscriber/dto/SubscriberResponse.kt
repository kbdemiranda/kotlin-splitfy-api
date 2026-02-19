package io.github.splitfy.api.web.subscriber.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "Subscriber data")
data class SubscriberResponse(
    @Schema(description = "Subscriber ID")
    val id: Long?,

    @Schema(description = "Subscriber name")
    val name: String,

    @Schema(description = "Subscriber email")
    val email: String,

    @Schema(description = "Created at")
    val createdAt: LocalDateTime,

    @Schema(description = "Updated at")
    val updatedAt: LocalDateTime? = null,

    @Schema(description = "Deleted at")
    val deletedAt: LocalDateTime? = null,

    @Schema(description = "Active subscriptions associated with subscriber (detail endpoint)")
    val subscriptions: List<SubscriptionItemResponse> = emptyList(),
)
