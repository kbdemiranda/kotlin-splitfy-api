package io.github.splitfy.api.web.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class ForgotPasswordRequest(
    @field:Email
    @field:NotBlank
    val email: String,
)
