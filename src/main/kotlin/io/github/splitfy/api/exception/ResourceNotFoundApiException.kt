package io.github.splitfy.api.exception

import org.springframework.http.HttpStatus

class ResourceNotFoundApiException(
    message: String,
    code: String = "NOT_FOUND",
) : ApiException(message = message, status = HttpStatus.NOT_FOUND, code = code)
