package io.github.splitfy.api.web.error

import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.exception.ResourceNotFoundApiException
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.jdbc.UncategorizedSQLException
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.server.ResponseStatusException
import java.sql.SQLException

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `handleDatabaseException does not expose SQL details`() {
        val request = MockHttpServletRequest("GET", "/users")
        val exception = UncategorizedSQLException(
            "select failed",
            "select * from users where email = ?",
            SQLException("relation users does not exist")
        )

        val response = handler.handleDatabaseException(exception, request)

        assertEquals(500, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals("DB_ERROR", response.body?.code)
        assertEquals("Database operation failed", response.body?.message)
    }

    @Test
    fun `handleConflict does not expose constraint details`() {
        val request = MockHttpServletRequest("POST", "/users")
        val exception = DataIntegrityViolationException(
            "duplicate key value violates unique constraint uk_users_email"
        )

        val response = handler.handleConflict(exception, request)

        assertEquals(409, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals("CONFLICT", response.body?.code)
        assertEquals(
            "Request could not be completed because it conflicts with existing data",
            response.body?.message
        )
    }

    @Test
    fun `handleUnexpectedException does not expose internal exception message`() {
        val request = MockHttpServletRequest("GET", "/billing")
        val exception = IllegalStateException("Null pointer while reading billing summary")

        val response = handler.handleUnexpectedException(exception, request)

        assertEquals(500, response.statusCode.value())
        assertNotNull(response.body)
        assertEquals("INTERNAL_SERVER_ERROR", response.body?.code)
        assertEquals("Unexpected internal error", response.body?.message)
    }

    @Test
    fun `handleApiException preserves api status code and message`() {
        val request = MockHttpServletRequest("GET", "/profiles/missing")
        val exception = ResourceNotFoundApiException("Profile not found")

        val response = handler.handleApiException(exception, request)

        assertEquals(404, response.statusCode.value())
        assertEquals("NOT_FOUND", response.body?.code)
        assertEquals("Profile not found", response.body?.message)
        assertEquals("/profiles/missing", response.body?.path)
    }

    @Test
    fun `handleValidationException joins field errors`() {
        val request = MockHttpServletRequest("POST", "/users")
        val bindingResult = BeanPropertyBindingResult(Any(), "request")
        bindingResult.addError(FieldError("request", "email", "must be a well-formed email address"))
        bindingResult.addError(FieldError("request", "password", "invalid value"))
        val method = ValidationTarget::class.java.getDeclaredMethod("handle", String::class.java)
        val exception = MethodArgumentNotValidException(MethodParameter(method, 0), bindingResult)

        val response = handler.handleValidationException(exception, request)

        assertEquals(400, response.statusCode.value())
        assertEquals("BAD_REQUEST", response.body?.code)
        assertEquals("email: must be a well-formed email address; password: invalid value", response.body?.message)
    }

    @Test
    fun `handleBadRequest uses exception message`() {
        val request = MockHttpServletRequest("POST", "/auth/logout")
        val exception = BadRequestApiException("Bad token")

        val response = handler.handleBadRequest(exception, request)

        assertEquals(400, response.statusCode.value())
        assertEquals("BAD_REQUEST", response.body?.code)
        assertEquals("Bad token", response.body?.message)
    }

    @Test
    fun `handleBadRequest handles unreadable body`() {
        val request = MockHttpServletRequest("POST", "/users")
        val exception = HttpMessageNotReadableException(
            "JSON parse error",
            MockHttpInputMessage(ByteArray(0))
        )

        val response = handler.handleBadRequest(exception, request)

        assertEquals(400, response.statusCode.value())
        assertEquals("BAD_REQUEST", response.body?.code)
    }

    @Test
    fun `handleNotFound maps entity not found exception`() {
        val request = MockHttpServletRequest("GET", "/platforms/99")

        val response = handler.handleNotFound(EntityNotFoundException("Missing entity"), request)

        assertEquals(404, response.statusCode.value())
        assertEquals("NOT_FOUND", response.body?.code)
        assertEquals("Missing entity", response.body?.message)
    }

    @Test
    fun `handleUnauthorized maps bad credentials`() {
        val request = MockHttpServletRequest("POST", "/auth/login")

        val response = handler.handleUnauthorized(BadCredentialsException("Invalid credentials"), request)

        assertEquals(401, response.statusCode.value())
        assertEquals("UNAUTHORIZED", response.body?.code)
        assertEquals("Invalid credentials", response.body?.message)
    }

    @Test
    fun `handleUnauthorized maps missing credentials`() {
        val request = MockHttpServletRequest("GET", "/dashboard/kpis")

        val response = handler.handleUnauthorized(AuthenticationCredentialsNotFoundException("No auth"), request)

        assertEquals(401, response.statusCode.value())
        assertEquals("UNAUTHORIZED", response.body?.code)
        assertEquals("No auth", response.body?.message)
    }

    @Test
    fun `handleForbidden maps access denied`() {
        val request = MockHttpServletRequest("DELETE", "/users/1")

        val response = handler.handleForbidden(AccessDeniedException("Forbidden"), request)

        assertEquals(403, response.statusCode.value())
        assertEquals("FORBIDDEN", response.body?.code)
        assertEquals("Forbidden", response.body?.message)
    }

    @Test
    fun `handleResponseStatusException uses reason and status`() {
        val request = MockHttpServletRequest("GET", "/anything")
        val exception = ResponseStatusException(HttpStatus.I_AM_A_TEAPOT, "Short and stout")

        val response = handler.handleResponseStatusException(exception, request)

        assertEquals(418, response.statusCode.value())
        assertEquals("418 I_AM_A_TEAPOT", response.body?.code)
        assertEquals("Short and stout", response.body?.message)
    }

    private class ValidationTarget {
        fun handle(value: String) = value
    }
}
