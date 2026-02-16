package io.github.splitfy.api.web.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Logout response payload")
data class LogoutResponse(
    @Schema(description = "Operation result message", example = "Logout successful")
    val message: String,
)
