package io.github.splitfy.api.exception

import org.springframework.http.HttpStatus

class TooManyRequestsApiException(
    message: String = "Too many requests. Please try again later."
) : ApiException(
    message = message,
    status = HttpStatus.TOO_MANY_REQUESTS,
    code = "RATE_LIMIT_EXCEEDED"
)
