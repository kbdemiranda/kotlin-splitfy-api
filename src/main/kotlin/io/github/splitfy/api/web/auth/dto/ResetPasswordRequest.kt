package io.github.splitfy.api.web.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class ResetPasswordRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^\\d{6}$", message = "Token must be a 6-digit code")
    val token: String,

    @field:NotBlank
    @field:Size(min = 8, max = 128)
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,128}$",
        message = "Password must contain uppercase, lowercase, number and special character"
    )
    val newPassword: String,
)
