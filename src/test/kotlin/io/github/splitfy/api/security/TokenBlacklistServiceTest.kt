package io.github.splitfy.api.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Instant

class TokenBlacklistServiceTest {

    private val redisTemplate: StringRedisTemplate = mock()
    private val valueOperations: ValueOperations<String, String> = mock()
    private val service = TokenBlacklistService(redisTemplate)

    init {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
    }

    @Test
    fun `blacklist stores hashed token with ttl until expiration`() {
        val token = "jwt-token"
        val expiresAt = Instant.now().plusSeconds(300)

        service.blacklist(token, expiresAt)

        verify(valueOperations).set(
            argThat<String> { startsWith("jwt-blacklist:") && !contains(token) },
            eq(expiresAt.toString()),
            any<java.time.Duration>()
        )
    }

    @Test
    fun `isBlacklisted checks hashed token key in redis`() {
        whenever(redisTemplate.hasKey(any())).thenReturn(true)

        val result = service.isBlacklisted("jwt-token")

        assertTrue(result)
        verify(redisTemplate).hasKey(argThat<String> { startsWith("jwt-blacklist:") && !contains("jwt-token") })
    }

    @Test
    fun `blacklist ignores expired token`() {
        service.blacklist("jwt-token", Instant.now().minusSeconds(1))

        verify(valueOperations, org.mockito.kotlin.never()).set(any(), any(), any<java.time.Duration>())
    }

    @Test
    fun `isBlacklisted returns false when redis has no key`() {
        whenever(redisTemplate.hasKey(any())).thenReturn(false)

        assertFalse(service.isBlacklisted("jwt-token"))
    }
}
