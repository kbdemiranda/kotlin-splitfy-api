package io.github.splitfy.api.web.auth

import io.github.splitfy.api.service.auth.AuthService
import io.github.splitfy.api.web.auth.dto.LoginRequest
import io.github.splitfy.api.web.auth.dto.LoginResponse
import io.github.splitfy.api.web.auth.dto.LogoutResponse
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
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        return ResponseEntity.ok(authService.login(request))
    }

    @PostMapping("/logout")
    fun logout(@RequestHeader("Authorization", required = false) authorizationHeader: String?): ResponseEntity<LogoutResponse> {
        if (authorizationHeader.isNullOrBlank() || !authorizationHeader.startsWith("Bearer ")) {
            throw IllegalArgumentException("Authorization Bearer token is required")
        }

        val token = authorizationHeader.removePrefix("Bearer ").trim()
        authService.logout(token)
        return ResponseEntity.status(HttpStatus.OK).body(LogoutResponse("Logout successful"))
    }
}
