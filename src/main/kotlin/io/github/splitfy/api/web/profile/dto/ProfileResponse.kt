package io.github.splitfy.api.web.profile.dto

import io.github.splitfy.api.domain.enums.ProfileName
import java.time.LocalDateTime
import java.util.UUID

data class ProfileResponse(
    val id: UUID,
    val name: ProfileName,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)
