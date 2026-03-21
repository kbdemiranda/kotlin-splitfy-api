package io.github.splitfy.api.web.subscriber.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Subscriber input data")
data class SubscriberRequest(
    @Schema(description = "Subscriber name", example = "Ana Silva")
    val name: String,
    @Schema(description = "Subscriber email", example = "ana.silva@example.com")
    val email: String,
    @Schema(description = "Financial responsible subscriber id. If omitted, defaults to the subscriber itself", example = "1")
    val financialResponsibleSubscriberId: Long? = null,
) {

    init {
        require(name.isNotBlank()) { "Name cannot be blank" }
        require(email.isNotBlank()) { "Email cannot be blank" }
    }
}
