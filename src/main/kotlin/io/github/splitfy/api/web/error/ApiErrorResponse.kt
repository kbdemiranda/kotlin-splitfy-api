package io.github.splitfy.api.web.error

import java.time.OffsetDateTime

data class ApiErrorResponse(
    val status: Int,
    val code: String,
    val message: String,
    val path: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now()
)
