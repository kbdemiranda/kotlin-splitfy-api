package io.github.splitfy.api.web.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

@Schema(description = "Login request payload")
data class LoginRequest(
    @field:Email
    @field:NotBlank
    @field:Schema(description = "User e-mail", example = "john.doe@example.com")
    val email: String,

    @field:NotBlank
    @field:Schema(description = "User password", example = "MySecurePass#123")
    val password: String,
)
