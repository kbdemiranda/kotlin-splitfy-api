package io.github.splitfy.api.web.user.dto

import io.github.splitfy.api.domain.enums.ProfileName
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size
import java.util.UUID

@Schema(description = "Update user request payload")
data class UserUpdateRequest(
    @field:Size(max = 120)
    @field:Schema(description = "User full name", example = "John Doe")
    val name: String? = null,

    @field:Email
    @field:Size(max = 255)
    @field:Schema(description = "User e-mail", example = "john.doe@example.com")
    val email: String? = null,

    @field:Size(min = 8, max = 120)
    @field:Schema(description = "User password", example = "MyUpdatedPass#123")
    val password: String? = null,

    @field:Schema(description = "Profile ID to associate with the user")
    val profileId: UUID? = null,

    @field:Schema(description = "Profile name to associate when profileId is not informed")
    val profileName: ProfileName? = null,

    @field:Schema(description = "Whether the user is enabled")
    val isEnabled: Boolean? = null,
)
