package io.github.splitfy.api.exception

import org.springframework.http.HttpStatus

class ConflictApiException(
    message: String,
    code: String = "CONFLICT",
) : ApiException(message = message, status = HttpStatus.CONFLICT, code = code)
