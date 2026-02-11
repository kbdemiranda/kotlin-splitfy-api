package io.github.splitfy.api.application.dto.input

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Subscriber input data")
data class SubscriberIn(
    @Schema(description = "Subscriber name", example = "Ana Silva")
    val name: String,
    @Schema(description = "Subscriber email", example = "ana.silva@example.com")
    val email: String
) {

    init {
        require(name.isNotBlank()) { "Name cannot be blank" }
        require(email.isNotBlank()) { "Email cannot be blank" }
    }
}
