package io.github.splitfy.api.web.user.dto

import io.github.splitfy.api.domain.enums.ProfileName
import java.time.LocalDateTime
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val name: String,
    val email: String,
    val profile: ProfileSummaryResponse?,
    val isEnabled: Boolean,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    val deletedAt: LocalDateTime?,
)

data class ProfileSummaryResponse(
    val id: UUID,
    val name: ProfileName,
)
