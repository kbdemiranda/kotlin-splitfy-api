package io.github.splitfy.api.exception

import org.springframework.http.HttpStatus

class UnauthorizedApiException(
    message: String,
    code: String = "UNAUTHORIZED",
) : ApiException(message = message, status = HttpStatus.UNAUTHORIZED, code = code)
