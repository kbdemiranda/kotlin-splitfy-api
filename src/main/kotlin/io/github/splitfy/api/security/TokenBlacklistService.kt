package io.github.splitfy.api.security

import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class TokenBlacklistService {

    private val blacklistedTokens = ConcurrentHashMap<String, Instant>()

    fun blacklist(token: String, expiresAt: Instant) {
        blacklistedTokens[token] = expiresAt
    }

    fun isBlacklisted(token: String): Boolean {
        cleanupExpired()
        return blacklistedTokens.containsKey(token)
    }

    private fun cleanupExpired() {
        val now = Instant.now()
        blacklistedTokens.entries.removeIf { it.value.isBefore(now) }
    }
}
