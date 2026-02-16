package io.github.splitfy.api.web.profile.dto

import io.github.splitfy.api.domain.enums.ProfileName
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.UUID

@Schema(description = "Profile response payload")
data class ProfileResponse(
    @Schema(description = "Profile ID")
    val id: UUID,
    @Schema(description = "Profile name")
    val name: ProfileName,
    @Schema(description = "Creation timestamp")
    val createdAt: LocalDateTime?,
    @Schema(description = "Last update timestamp")
    val updatedAt: LocalDateTime?,
)
