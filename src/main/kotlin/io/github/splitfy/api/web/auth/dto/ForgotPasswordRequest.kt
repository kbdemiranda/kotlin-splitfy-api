package io.github.splitfy.api.web.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

@Schema(description = "Forgot password request payload")
data class ForgotPasswordRequest(
    @field:Email
    @field:NotBlank
    @field:Schema(description = "User e-mail", example = "john.doe@example.com")
    val email: String,
)
