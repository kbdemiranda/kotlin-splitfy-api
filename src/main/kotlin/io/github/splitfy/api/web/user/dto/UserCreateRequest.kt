package io.github.splitfy.api.web.user.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

data class UserCreateRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val name: String,

    @field:Email
    @field:NotBlank
    @field:Size(max = 255)
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, max = 120)
    val password: String,

    @field:NotNull
    val profileId: UUID,

    val isEnabled: Boolean = true,
)
