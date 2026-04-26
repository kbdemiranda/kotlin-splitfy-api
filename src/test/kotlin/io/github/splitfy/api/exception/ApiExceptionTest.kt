package io.github.splitfy.api.exception

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class ApiExceptionTest {

    @Test
    fun `too many requests exception exposes default status code and message`() {
        val exception = TooManyRequestsApiException()

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.status)
        assertEquals("RATE_LIMIT_EXCEEDED", exception.code)
        assertEquals("Too many requests. Please try again later.", exception.message)
    }

    @Test
    fun `too many requests exception supports custom message`() {
        val exception = TooManyRequestsApiException("Slow down")

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.status)
        assertEquals("RATE_LIMIT_EXCEEDED", exception.code)
        assertEquals("Slow down", exception.message)
    }
}
