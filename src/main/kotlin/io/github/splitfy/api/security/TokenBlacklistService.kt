package io.github.splitfy.api.security

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

@Service
class TokenBlacklistService(
    private val redisTemplate: StringRedisTemplate,
) {

    fun blacklist(token: String, expiresAt: Instant) {
        val ttl = Duration.between(Instant.now(), expiresAt)
        if (ttl.isNegative || ttl.isZero) {
            return
        }

        redisTemplate.opsForValue().set(cacheKey(token), expiresAt.toString(), ttl)
    }

    fun isBlacklisted(token: String): Boolean {
        return redisTemplate.hasKey(cacheKey(token)) == true
    }

    private fun cacheKey(token: String): String = "$CACHE_KEY_PREFIX:${hashToken(token)}"

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashedBytes = digest.digest(token.toByteArray(StandardCharsets.UTF_8))
        return hashedBytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val CACHE_KEY_PREFIX = "jwt-blacklist"
    }
}
