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
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "Authentication", description = "Authentication and password recovery operations")
class AuthController(
    private val authService: AuthService,
    private val authRateLimitService: AuthRateLimitService,
) {

    @Operation(summary = "Login", description = "Authenticates a user and returns a JWT token")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Authentication successful"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "401", description = "Invalid credentials"),
            ApiResponse(responseCode = "429", description = "Too many login attempts")
        ]
    )
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        @Parameter(hidden = true)
        servletRequest: HttpServletRequest
    ): ResponseEntity<LoginResponse> {
        authRateLimitService.checkLoginAllowed(clientIp(servletRequest), request.email)
        return ResponseEntity.ok(authService.login(request))
    }

    @Operation(summary = "Request password reset", description = "Sends a password reset token to the user e-mail")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Password reset instructions sent"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "429", description = "Too many password reset attempts")
        ]
    )
    @PostMapping("/forgot-password")
    fun forgotPassword(
        @Valid @RequestBody request: ForgotPasswordRequest,
        @Parameter(hidden = true)
        servletRequest: HttpServletRequest
    ): ResponseEntity<SimpleMessageResponse> {
        val clientIp = clientIp(servletRequest)
        authRateLimitService.checkForgotPasswordAllowed(clientIp, request.email)
        return ResponseEntity.ok(authService.forgotPassword(request, clientIp))
    }

    @Operation(summary = "Reset password", description = "Resets the user password using a valid reset token")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Password updated successfully"),
            ApiResponse(responseCode = "400", description = "Invalid token or password")
        ]
    )
    @PostMapping("/reset-password")
    fun resetPassword(
        @Valid @RequestBody request: ResetPasswordRequest,
        @Parameter(hidden = true)
        servletRequest: HttpServletRequest
    ): ResponseEntity<SimpleMessageResponse> {
        authRateLimitService.checkResetPasswordAllowed(clientIp(servletRequest), authService.tokenFingerprint(request.token))
        return ResponseEntity.ok(authService.resetPassword(request))
    }

    @Operation(summary = "Logout", description = "Revokes the provided JWT token")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Logout successful"),
            ApiResponse(responseCode = "400", description = "Authorization Bearer token is required")
        ]
    )
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
        // Do not trust X-Forwarded-For unless the app is behind a known proxy chain.
        return request.remoteAddr?.takeIf { it.isNotBlank() } ?: "unknown"
    }
}
