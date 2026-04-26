package io.github.splitfy.api.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.slf4j.Logger

@ExtendWith(MockitoExtension::class)
class SemanticLogTest {

    @Mock
    lateinit var logger: Logger

    @Test
    fun `infoEvent sanitizes values and omits null fields`() {
        logger.infoEvent(
            event = "billing summary",
            "subscriber" to "Ana Souza",
            "notes" to "line 1\nline 2",
            "missing" to null,
        )

        val captor = argumentCaptor<String>()
        verify(logger).info(captor.capture())
        assertEquals("event=billing summary subscriber=Ana_Souza notes=line_1_line_2", captor.firstValue)
        assertFalse(captor.firstValue.contains("missing"))
    }

    @Test
    fun `warnEvent logs semantic message without throwable`() {
        logger.warnEvent("auth.limit", "email" to "user@example.com")

        val captor = argumentCaptor<String>()
        verify(logger).warn(captor.capture())
        assertEquals("event=auth.limit email=user@example.com", captor.firstValue)
    }

    @Test
    fun `warnEvent logs semantic message with throwable`() {
        val exception = IllegalStateException("boom")

        logger.warnEvent("email.failed", exception, "attempt" to 2)

        val captor = argumentCaptor<String>()
        verify(logger).warn(captor.capture(), same(exception))
        assertEquals("event=email.failed attempt=2", captor.firstValue)
    }

    @Test
    fun `errorEvent logs semantic message with throwable`() {
        val exception = IllegalArgumentException("boom")

        logger.errorEvent("http.request.exception", exception, "status" to 500)

        val captor = argumentCaptor<String>()
        verify(logger).error(captor.capture(), same(exception))
        assertEquals("event=http.request.exception status=500", captor.firstValue)
    }

    @Test
    fun `email log context exposes event entity token and metadata`() {
        val context = EmailLogContext(
            event = "email.sent",
            entity = "subscriber",
            entityId = 42L,
            entityToken = "abc",
            metadata = mapOf("referenceMonth" to "2026-04")
        )

        assertEquals("email.sent", context.event)
        assertEquals("subscriber", context.entity)
        assertEquals(42L, context.entityId)
        assertEquals("abc", context.entityToken)
        assertEquals("2026-04", context.metadata["referenceMonth"])
    }
}
