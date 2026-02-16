package io.github.splitfy.api.web.profile.dto

import io.github.splitfy.api.domain.enums.ProfileName
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "Profile request payload")
data class ProfileRequest(
    @field:NotNull
    @field:Schema(description = "Profile name", example = "ADMIN")
    val name: ProfileName,
)
