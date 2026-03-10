package io.github.splitfy.api.service.auth

import io.github.splitfy.api.exception.TooManyRequestsApiException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AuthRateLimitServiceTest {

    private val properties = AuthRateLimitProperties().apply {
        loginByIpPerMinute = 2
        loginByEmailPerMinute = 2
        forgotByIpPerHour = 2
        forgotByEmailPerHour = 2
        resetByIpPerHour = 2
        resetByTokenPerHour = 2
    }
    private val redisTemplate: StringRedisTemplate = mock()
    private val valueOperations: ValueOperations<String, String> = mock()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC)
    private val service = AuthRateLimitService(properties, redisTemplate, clock)

    init {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
    }

    @Test
    fun `checkLoginAllowed increments redis counters and sets ttl on first hit`() {
        whenever(valueOperations.increment("auth-rate-limit:login:ip:127.0.0.1")).thenReturn(1L)
        whenever(valueOperations.increment("auth-rate-limit:login:email:user@example.com")).thenReturn(1L)

        assertDoesNotThrow {
            service.checkLoginAllowed("127.0.0.1", "user@example.com")
        }

        verify(redisTemplate).expire(eq("auth-rate-limit:login:ip:127.0.0.1"), any())
        verify(redisTemplate).expire(eq("auth-rate-limit:login:email:user@example.com"), any())
    }

    @Test
    fun `checkResetPasswordAllowed throws when token counter exceeds limit`() {
        whenever(valueOperations.increment("auth-rate-limit:reset:ip:127.0.0.1")).thenReturn(1L)
        whenever(valueOperations.increment("auth-rate-limit:reset:token:token-fingerprint")).thenReturn(3L)

        assertThrows(TooManyRequestsApiException::class.java) {
            service.checkResetPasswordAllowed("127.0.0.1", "token-fingerprint")
        }
    }

    @Test
    fun `service fails open when redis increment errors`() {
        whenever(valueOperations.increment(any())).thenThrow(RuntimeException("redis unavailable"))

        assertDoesNotThrow {
            service.checkForgotPasswordAllowed("127.0.0.1", "user@example.com")
        }
    }
}
