package io.github.splitfy.api.service.auth

import io.github.splitfy.api.domain.entity.User
import io.github.splitfy.api.domain.entity.PasswordResetToken
import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.repository.PasswordResetTokenRepository
import io.github.splitfy.api.repository.UserRepository
import io.github.splitfy.api.security.JwtProperties
import io.github.splitfy.api.security.JwtService
import io.github.splitfy.api.security.TokenBlacklistService
import io.github.splitfy.api.service.email.EmailService
import io.github.splitfy.api.web.auth.dto.ForgotPasswordRequest
import io.github.splitfy.api.web.auth.dto.ResetPasswordRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
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
    private val passwordResetTokenRepository: PasswordResetTokenRepository = mock()
    private val jwtService: JwtService = mock()
    private val tokenBlacklistService: TokenBlacklistService = mock()
    private val passwordEncoder: PasswordEncoder = mock()
    private val emailService: EmailService = mock()

    private val service = AuthService(
        authenticationManager = authenticationManager,
        userRepository = userRepository,
        passwordResetTokenRepository = passwordResetTokenRepository,
        jwtService = jwtService,
        tokenBlacklistService = tokenBlacklistService,
        jwtProperties = JwtProperties(secret = "test", expirationMs = 3600000),
        passwordEncoder = passwordEncoder,
        emailService = emailService,
        resetTokenExpirationMinutes = 30,
    )

    @Test
    fun `forgotPassword creates token and sends email when user exists and is enabled`() {
        val user = activeUser(email = "user@example.com")

        whenever(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("user@example.com")).thenReturn(user)
        whenever(passwordResetTokenRepository.save(any())).thenAnswer { it.getArgument(0) as PasswordResetToken }

        val response = service.forgotPassword(ForgotPasswordRequest(email = "user@example.com"), "127.0.0.1")

        assertEquals("If the email is registered, reset instructions have been sent.", response.message)
        verify(passwordResetTokenRepository, times(1)).invalidateAllActiveByUserId(eq(user.id!!), any())
        verify(passwordResetTokenRepository, times(1)).save(argThat {
            this.user == user &&
                this.requestedIp == "127.0.0.1" &&
                this.tokenHash.length == 64
        })
        verify(emailService, times(1)).sendHtml(eq("user@example.com"), any(), any())
        verify(userRepository, never()).save(any())
        verify(passwordEncoder, never()).encode(any())
    }

    @Test
    fun `forgotPassword does not create token when user is disabled`() {
        val user = activeUser(email = "disabled@example.com").copy(isEnabled = false)

        whenever(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("disabled@example.com")).thenReturn(user)

        val response = service.forgotPassword(ForgotPasswordRequest(email = "disabled@example.com"), "127.0.0.1")

        assertEquals("If the email is registered, reset instructions have been sent.", response.message)
        verify(passwordEncoder, never()).encode(any())
        verify(userRepository, never()).save(any())
        verify(emailService, never()).sendHtml(any(), any(), any())
        verify(passwordResetTokenRepository, never()).save(any())
    }

    @Test
    fun `forgotPassword does nothing when user does not exist`() {
        whenever(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("unknown@example.com")).thenReturn(null)

        val response = service.forgotPassword(ForgotPasswordRequest(email = "unknown@example.com"), "127.0.0.1")

        assertEquals("If the email is registered, reset instructions have been sent.", response.message)
        verify(passwordEncoder, never()).encode(any())
        verify(userRepository, never()).save(any())
        verify(emailService, never()).sendHtml(any(), any(), any())
        verify(passwordResetTokenRepository, never()).save(any())
    }

    @Test
    fun `resetPassword updates user password and invalidates tokens`() {
        val user = activeUser(email = "reset@example.com")
        val token = PasswordResetToken(
            id = 1L,
            user = user,
            tokenHash = "abc",
            expiresAt = LocalDateTime.now().plusMinutes(10)
        )

        whenever(passwordResetTokenRepository.findActiveByTokenHash(any(), any())).thenReturn(token)
        whenever(passwordEncoder.encode("New@Password1")).thenReturn("encoded-new-password")
        whenever(userRepository.save(any())).thenAnswer { it.getArgument(0) as User }

        val response = service.resetPassword(ResetPasswordRequest(token = "raw-token", newPassword = "New@Password1"))

        assertEquals("Password reset successful.", response.message)
        verify(userRepository, times(1)).save(argThat { password == "encoded-new-password" })
        verify(passwordResetTokenRepository, times(1)).invalidateAllActiveByUserId(eq(user.id!!), any())
    }

    @Test
    fun `resetPassword throws when token is invalid`() {
        whenever(passwordResetTokenRepository.findActiveByTokenHash(any(), any())).thenReturn(null)

        val ex = assertThrows(BadRequestApiException::class.java) {
            service.resetPassword(ResetPasswordRequest(token = "invalid", newPassword = "New@Password1"))
        }

        assertNotNull(ex)
        verify(userRepository, never()).save(any())
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
