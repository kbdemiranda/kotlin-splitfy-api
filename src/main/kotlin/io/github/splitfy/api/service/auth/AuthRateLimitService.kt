package io.github.splitfy.api.service.auth

import io.github.splitfy.api.exception.TooManyRequestsApiException
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Service
class AuthRateLimitService(
    private val properties: AuthRateLimitProperties,
) {

    private val windows = ConcurrentHashMap<String, WindowState>()
    private val operations = AtomicLong(0)
    private val clock: Clock = Clock.systemUTC()

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

    private fun enforce(key: String, maxRequests: Int, duration: Duration, message: String) {
        val windowMillis = duration.toMillis()
        val now = clock.millis()
        val state = windows.computeIfAbsent(key) { WindowState(now, 0, windowMillis) }

        val currentCount = synchronized(state) {
            if (now - state.startedAtMs >= state.windowMs) {
                state.startedAtMs = now
                state.count = 0
            }
            state.count += 1
            state.count
        }

        if (currentCount > maxRequests) {
            throw TooManyRequestsApiException(message)
        }

        if (operations.incrementAndGet() % 100L == 0L) {
            cleanup(now)
        }
    }

    private fun cleanup(now: Long) {
        val iterator = windows.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val state = entry.value
            val shouldRemove = synchronized(state) {
                now - state.startedAtMs >= state.windowMs * 3
            }
            if (shouldRemove) {
                iterator.remove()
            }
        }
    }

    private data class WindowState(
        var startedAtMs: Long,
        var count: Int,
        val windowMs: Long,
    )
}
