package io.github.splitfy.api.web.user.dto

import io.github.splitfy.api.domain.enums.ProfileName
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

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

    @field:Schema(description = "Profile ID to associate with the user", example = "8f9d6d52-2f1b-4b1a-89fd-3121df7d0f33")
    val profileId: UUID? = null,

    @field:Schema(description = "Profile name to associate when profileId is not informed", example = "VIEWER")
    val profileName: ProfileName? = null,

    @field:Schema(description = "Whether the user is enabled", example = "true")
    val isEnabled: Boolean = true,
)
