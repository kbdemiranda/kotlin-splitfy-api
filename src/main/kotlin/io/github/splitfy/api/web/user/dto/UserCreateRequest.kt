package io.github.splitfy.api.web.user.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "Create user request payload")
data class UserCreateRequest(
    @field:NotBlank
    @field:Size(max = 120)
    @field:Schema(description = "User full name", example = "John Doe")
    val name: String,

    @field:Email
    @field:NotBlank
    @field:Size(max = 255)
    @field:Schema(description = "User e-mail", example = "john.doe@example.com")
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, max = 120)
    @field:Schema(description = "User password", example = "MySecurePass#123")
    val password: String,

    @field:Schema(description = "Whether the user is enabled", example = "true")
    val isEnabled: Boolean = true,
)
