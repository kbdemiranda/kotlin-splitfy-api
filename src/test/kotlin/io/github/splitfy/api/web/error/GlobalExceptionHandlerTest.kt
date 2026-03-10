package io.github.splitfy.api.web.error

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.UncategorizedSQLException
import org.springframework.mock.web.MockHttpServletRequest
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
}
