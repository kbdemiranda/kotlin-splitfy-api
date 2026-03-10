package io.github.splitfy.api.web.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(description = "Reset password request payload")
data class ResetPasswordRequest(
    @field:NotBlank
    @field:Size(min = 43, max = 128)
    @field:Pattern(regexp = "^[A-Za-z0-9_-]{43,128}$", message = "Token must be a URL-safe reset token")
    @field:Schema(description = "URL-safe reset token", example = "S7A6B8NINzV0vDgCk3iybB24wL-r2I8M7VJt1o8C2aM")
    val token: String,

    @field:NotBlank
    @field:Size(min = 8, max = 128)
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,128}$",
        message = "Password must contain uppercase, lowercase, number and special character"
    )
    @field:Schema(description = "New password", example = "MyNewPass#2026")
    val newPassword: String,
)
