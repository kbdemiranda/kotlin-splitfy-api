package io.github.splitfy.api.web.auth

import io.github.splitfy.api.service.auth.AuthService
import io.github.splitfy.api.service.auth.AuthRateLimitService
import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.web.auth.dto.ForgotPasswordRequest
import io.github.splitfy.api.web.auth.dto.LoginRequest
import io.github.splitfy.api.web.auth.dto.LoginResponse
import io.github.splitfy.api.web.auth.dto.LogoutResponse
import io.github.splitfy.api.web.auth.dto.ResetPasswordRequest
import io.github.splitfy.api.web.auth.dto.SimpleMessageResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    private val authRateLimitService: AuthRateLimitService,
) {

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<LoginResponse> {
        authRateLimitService.checkLoginAllowed(clientIp(servletRequest), request.email)
        return ResponseEntity.ok(authService.login(request))
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(
        @Valid @RequestBody request: ForgotPasswordRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<SimpleMessageResponse> {
        val clientIp = clientIp(servletRequest)
        authRateLimitService.checkForgotPasswordAllowed(clientIp, request.email)
        return ResponseEntity.ok(authService.forgotPassword(request, clientIp))
    }

    @PostMapping("/reset-password")
    fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<SimpleMessageResponse> {
        return ResponseEntity.ok(authService.resetPassword(request))
    }

    @PostMapping("/logout")
    fun logout(@RequestHeader("Authorization", required = false) authorizationHeader: String?): ResponseEntity<LogoutResponse> {
        if (authorizationHeader.isNullOrBlank() || !authorizationHeader.startsWith("Bearer ")) {
            throw BadRequestApiException("Authorization Bearer token is required")
        }

        val token = authorizationHeader.removePrefix("Bearer ").trim()
        authService.logout(token)
        return ResponseEntity.status(HttpStatus.OK).body(LogoutResponse("Logout successful"))
    }

    private fun clientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return forwarded ?: request.remoteAddr ?: "unknown"
    }
}
