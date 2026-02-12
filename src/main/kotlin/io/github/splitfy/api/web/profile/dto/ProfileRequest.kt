package io.github.splitfy.api.web.profile.dto

import io.github.splitfy.api.domain.enums.ProfileName
import jakarta.validation.constraints.NotNull

data class ProfileRequest(
    @field:NotNull
    val name: ProfileName,
)
