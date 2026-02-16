package io.github.splitfy.api.web.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Simple message response payload")
data class SimpleMessageResponse(
    @Schema(description = "Operation result message", example = "Password reset instructions sent")
    val message: String,
)
