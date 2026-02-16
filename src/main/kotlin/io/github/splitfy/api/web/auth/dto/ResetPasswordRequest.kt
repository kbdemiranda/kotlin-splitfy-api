package io.github.splitfy.api.web.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(description = "Reset password request payload")
data class ResetPasswordRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^\\d{6}$", message = "Token must be a 6-digit code")
    @field:Schema(description = "Six-digit reset token", example = "123456")
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
