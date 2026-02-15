package io.github.splitfy.api.service.auth

import io.github.splitfy.api.domain.entity.User
import io.github.splitfy.api.repository.UserRepository
import io.github.splitfy.api.security.JwtProperties
import io.github.splitfy.api.security.JwtService
import io.github.splitfy.api.security.TokenBlacklistService
import io.github.splitfy.api.service.email.EmailService
import io.github.splitfy.api.web.auth.dto.ForgotPasswordRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime
import java.util.UUID

class AuthServiceTest {

    private val authenticationManager: AuthenticationManager = mock()
    private val userRepository: UserRepository = mock()
    private val jwtService: JwtService = mock()
    private val tokenBlacklistService: TokenBlacklistService = mock()
    private val passwordEncoder: PasswordEncoder = mock()
    private val emailService: EmailService = mock()

    private val service = AuthService(
        authenticationManager = authenticationManager,
        userRepository = userRepository,
        jwtService = jwtService,
        tokenBlacklistService = tokenBlacklistService,
        jwtProperties = JwtProperties(secret = "test", expirationMs = 3600000),
        passwordEncoder = passwordEncoder,
        emailService = emailService,
    )

    @Test
    fun `forgotPassword resets password and sends email when user exists and is enabled`() {
        val user = activeUser(email = "user@example.com")

        whenever(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("user@example.com")).thenReturn(user)
        whenever(passwordEncoder.encode(any())).thenReturn("encoded-temp-password")
        whenever(userRepository.save(any())).thenAnswer { it.getArgument(0) as User }

        val response = service.forgotPassword(ForgotPasswordRequest(email = "user@example.com"))

        assertEquals("If the email is registered, a new password has been sent.", response.message)
        verify(passwordEncoder, times(1)).encode(any())
        verify(userRepository, times(1)).save(argThat { password == "encoded-temp-password" })
        verify(emailService, times(1)).sendHtml(eq("user@example.com"), any(), any())
    }

    @Test
    fun `forgotPassword does not reset password when user is disabled`() {
        val user = activeUser(email = "disabled@example.com").copy(isEnabled = false)

        whenever(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("disabled@example.com")).thenReturn(user)

        val response = service.forgotPassword(ForgotPasswordRequest(email = "disabled@example.com"))

        assertEquals("If the email is registered, a new password has been sent.", response.message)
        verify(passwordEncoder, never()).encode(any())
        verify(userRepository, never()).save(any())
        verify(emailService, never()).sendHtml(any(), any(), any())
    }

    @Test
    fun `forgotPassword does nothing when user does not exist`() {
        whenever(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("unknown@example.com")).thenReturn(null)

        val response = service.forgotPassword(ForgotPasswordRequest(email = "unknown@example.com"))

        assertEquals("If the email is registered, a new password has been sent.", response.message)
        verify(passwordEncoder, never()).encode(any())
        verify(userRepository, never()).save(any())
        verify(emailService, never()).sendHtml(any(), any(), any())
    }

    private fun activeUser(email: String): User {
        return User(
            id = UUID.randomUUID(),
            name = "User",
            email = email,
            password = "old-password",
            isEnabled = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
    }
}
