package io.github.splitfy.api.web.auth.dto

import io.github.splitfy.api.domain.enums.ProfileName
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "Login response payload")
data class LoginResponse(
    @Schema(description = "JWT access token")
    val token: String,
    @Schema(description = "Token type", example = "Bearer")
    val tokenType: String = "Bearer",
    @Schema(description = "Token expiration in milliseconds", example = "3600000")
    val expiresInMs: Long,
    @Schema(description = "Authenticated user ID")
    val userId: UUID,
    @Schema(description = "Authenticated user e-mail", example = "john.doe@example.com")
    val email: String,
    @Schema(description = "Authenticated user profile")
    val profile: ProfileName?,
)
