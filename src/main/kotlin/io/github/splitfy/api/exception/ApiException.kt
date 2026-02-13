package io.github.splitfy.api.exception

import org.springframework.http.HttpStatus

open class ApiException(
    message: String,
    val status: HttpStatus,
    val code: String,
) : RuntimeException(message)
