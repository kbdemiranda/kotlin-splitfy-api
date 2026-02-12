package io.github.splitfy.api.web.user.dto

import io.github.splitfy.api.domain.enums.ProfileName
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size
import java.util.UUID

data class UserUpdateRequest(
    @field:Size(max = 120)
    val name: String? = null,

    @field:Email
    @field:Size(max = 255)
    val email: String? = null,

    @field:Size(min = 8, max = 120)
    val password: String? = null,

    val profileId: UUID? = null,

    val profileName: ProfileName? = null,

    val isEnabled: Boolean? = null,
)
