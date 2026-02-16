package io.github.splitfy.api.web.error

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "Standard API error response payload")
data class ApiErrorResponse(
    @Schema(description = "HTTP status code", example = "400")
    val status: Int,
    @Schema(description = "Application-specific error code", example = "BAD_REQUEST")
    val code: String,
    @Schema(description = "Error message", example = "Validation error")
    val message: String,
    @Schema(description = "Request path that produced the error", example = "/users")
    val path: String,
    @Schema(description = "Timestamp when the error happened")
    val timestamp: OffsetDateTime = OffsetDateTime.now()
)
