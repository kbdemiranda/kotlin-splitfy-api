package io.github.splitfy.api.web.auth.dto

import io.github.splitfy.api.domain.enums.ProfileName
import java.util.UUID

data class LoginResponse(
    val token: String,
    val tokenType: String = "Bearer",
    val expiresInMs: Long,
    val userId: UUID,
    val email: String,
    val profile: ProfileName?,
)
