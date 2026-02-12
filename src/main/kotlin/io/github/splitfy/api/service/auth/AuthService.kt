package io.github.splitfy.api.service.auth

import io.github.splitfy.api.repository.UserRepository
import io.github.splitfy.api.security.JwtProperties
import io.github.splitfy.api.security.JwtService
import io.github.splitfy.api.security.TokenBlacklistService
import io.github.splitfy.api.web.auth.dto.LoginRequest
import io.github.splitfy.api.web.auth.dto.LoginResponse
import jakarta.persistence.EntityNotFoundException
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val authenticationManager: AuthenticationManager,
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val tokenBlacklistService: TokenBlacklistService,
    private val jwtProperties: JwtProperties,
) {

    fun login(request: LoginRequest): LoginResponse {
        runCatching {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(request.email, request.password),
            )
        }.onFailure {
            when (it) {
                is DisabledException -> throw DisabledException("User is disabled")
                else -> throw BadCredentialsException("Invalid email or password")
            }
        }

        val user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.email)
            ?: throw EntityNotFoundException("User not found")

        val token = jwtService.generateToken(user)

        val userId = user.id ?: throw IllegalStateException("User ID must not be null")
        return LoginResponse(
            token = token,
            expiresInMs = jwtProperties.expirationMs,
            userId = userId,
            email = user.email,
            profile = user.profile?.name,
        )
    }

    fun logout(token: String) {
        val expiresAt = jwtService.extractExpiration(token)
        tokenBlacklistService.blacklist(token, expiresAt)
    }
}
