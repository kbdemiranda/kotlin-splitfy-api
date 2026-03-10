package io.github.splitfy.api.service.auth

import io.github.splitfy.api.exception.TooManyRequestsApiException
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

@Service
class AuthRateLimitService(
    private val properties: AuthRateLimitProperties,
    private val redisTemplate: StringRedisTemplate,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(AuthRateLimitService::class.java)

    fun checkLoginAllowed(clientIp: String, email: String) {
        val normalizedEmail = email.trim().lowercase()
        enforce(
            key = "login:ip:$clientIp",
            maxRequests = properties.loginByIpPerMinute,
            duration = Duration.ofMinutes(1),
            message = "Too many login attempts from your IP. Please try again in a minute."
        )
        enforce(
            key = "login:email:$normalizedEmail",
            maxRequests = properties.loginByEmailPerMinute,
            duration = Duration.ofMinutes(1),
            message = "Too many login attempts for this account. Please try again in a minute."
        )
    }

    fun checkForgotPasswordAllowed(clientIp: String, email: String) {
        val normalizedEmail = email.trim().lowercase()
        enforce(
            key = "forgot:ip:$clientIp",
            maxRequests = properties.forgotByIpPerHour,
            duration = Duration.ofHours(1),
            message = "Too many password reset requests from your IP. Please try again later."
        )
        enforce(
            key = "forgot:email:$normalizedEmail",
            maxRequests = properties.forgotByEmailPerHour,
            duration = Duration.ofHours(1),
            message = "Too many password reset requests for this account. Please try again later."
        )
    }

    fun checkResetPasswordAllowed(clientIp: String, tokenFingerprint: String) {
        enforce(
            key = "reset:ip:$clientIp",
            maxRequests = properties.resetByIpPerHour,
            duration = Duration.ofHours(1),
            message = "Too many password reset attempts from your IP. Please try again later."
        )
        enforce(
            key = "reset:token:$tokenFingerprint",
            maxRequests = properties.resetByTokenPerHour,
            duration = Duration.ofHours(1),
            message = "Too many password reset attempts for this token. Request a new reset link."
        )
    }

    private fun enforce(key: String, maxRequests: Int, duration: Duration, message: String) {
        val redisKey = cacheKey(key)
        val currentCount = runCatching {
            val count = redisTemplate.opsForValue().increment(redisKey) ?: 0L
            if (count == 1L) {
                redisTemplate.expire(redisKey, duration)
            }
            count
        }.onFailure { ex ->
            log.warn("Failed to enforce auth rate limit for key {}", key, ex)
        }.getOrDefault(0L)

        if (currentCount == 0L) {
            return
        }

        if (currentCount > maxRequests) {
            throw TooManyRequestsApiException(message)
        }
    }

    private fun cacheKey(key: String): String = "$CACHE_KEY_PREFIX:$key"

    companion object {
        private const val CACHE_KEY_PREFIX = "auth-rate-limit"
    }
}
