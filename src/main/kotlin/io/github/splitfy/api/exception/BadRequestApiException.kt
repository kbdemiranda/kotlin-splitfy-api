package io.github.splitfy.api.exception

import org.springframework.http.HttpStatus

class BadRequestApiException(
    message: String,
    code: String = "BAD_REQUEST",
) : ApiException(message = message, status = HttpStatus.BAD_REQUEST, code = code)
