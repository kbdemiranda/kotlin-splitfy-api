package io.github.splitfy.api.web.user.dto

import io.github.splitfy.api.domain.enums.ProfileName
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.UUID

@Schema(description = "User response payload")
data class UserResponse(
    @Schema(description = "User ID")
    val id: UUID,
    @Schema(description = "User full name", example = "John Doe")
    val name: String,
    @Schema(description = "User e-mail", example = "john.doe@example.com")
    val email: String,
    @Schema(description = "Associated profile data")
    val profile: ProfileSummaryResponse?,
    @Schema(description = "Whether the user is enabled")
    val isEnabled: Boolean,
    @Schema(description = "Creation timestamp")
    val createdAt: LocalDateTime?,
    @Schema(description = "Last update timestamp")
    val updatedAt: LocalDateTime?,
    @Schema(description = "Deletion timestamp")
    val deletedAt: LocalDateTime?,
)

@Schema(description = "Profile summary payload")
data class ProfileSummaryResponse(
    @Schema(description = "Profile ID")
    val id: UUID,
    @Schema(description = "Profile name")
    val name: ProfileName,
)
