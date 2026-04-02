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
import io.github.splitfy.api.service.email.EmailTemplateService
import io.github.splitfy.api.web.auth.dto.ForgotPasswordRequest
import io.github.splitfy.api.web.auth.dto.LoginRequest
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
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.util.Date
import java.util.UUID

class AuthServiceTest {

    private val authenticationManager: AuthenticationManager = mock()
    private val userRepository: UserRepository = mock()
    private val passwordResetTokenRepository: PasswordResetTokenRepository = mock()
    private val jwtService: JwtService = mock()
    private val tokenBlacklistService: TokenBlacklistService = mock()
    private val passwordEncoder: PasswordEncoder = mock()
    private val emailService: EmailService = mock()
    private val emailTemplateService = EmailTemplateService()

    private val service = AuthService(
        authenticationManager = authenticationManager,
        userRepository = userRepository,
        passwordResetTokenRepository = passwordResetTokenRepository,
        jwtService = jwtService,
        tokenBlacklistService = tokenBlacklistService,
        jwtProperties = JwtProperties(secret = "test", expirationMs = 3600000),
        passwordEncoder = passwordEncoder,
        emailService = emailService,
        emailTemplateService = emailTemplateService,
        resetTokenExpirationMinutes = 15,
        resetTokenMaxAttempts = 5,
    )

    @Test
    fun `login returns response when credentials are valid`() {
        val request = LoginRequest(email = "test@example.com", password = "Password123")
        val user = activeUser(email = "test@example.com")

        whenever(authenticationManager.authenticate(any())).thenReturn(UsernamePasswordAuthenticationToken(user, null))
        whenever(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("test@example.com")).thenReturn(user)
        whenever(jwtService.generateToken(user)).thenReturn("mocked-jwt-token")

        val response = service.login(request)

        assertEquals("mocked-jwt-token", response.token)
        assertEquals(user.id, response.userId)
        assertEquals(user.email, response.email)
        verify(authenticationManager).authenticate(argThat {
            (this as UsernamePasswordAuthenticationToken).principal == "test@example.com" &&
                this.credentials == "Password123"
        })
    }

    @Test
    fun `login throws UnauthorizedApiException when credentials are invalid`() {
        val request = LoginRequest(email = "test@example.com", password = "WrongPassword")

        whenever(authenticationManager.authenticate(any())).thenThrow(org.springframework.security.authentication.BadCredentialsException("Invalid credentials"))

        val ex = assertThrows(io.github.splitfy.api.exception.UnauthorizedApiException::class.java) {
            service.login(request)
        }

        assertEquals("Invalid email or password", ex.message)
    }

    @Test
    fun `login throws UnauthorizedApiException with specific code when user is disabled`() {
        val request = LoginRequest(email = "disabled@example.com", password = "Password123")

        whenever(authenticationManager.authenticate(any())).thenThrow(DisabledException("User is disabled"))

        val ex = assertThrows(io.github.splitfy.api.exception.UnauthorizedApiException::class.java) {
            service.login(request)
        }

        assertEquals("User is disabled", ex.message)
        assertEquals("ACCOUNT_DISABLED", ex.code)
    }

    @Test
    fun `logout blacklists the token`() {
        val token = "some-jwt-token"
        val expirationDate = Instant.now().plusSeconds(3600)

        whenever(jwtService.extractExpiration(token)).thenReturn(expirationDate)

        service.logout(token)

        verify(tokenBlacklistService).blacklist(token, expirationDate)
    }

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
        verify(emailService, times(1)).sendHtml(eq("user@example.com"), any(), any(), any())
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
        verify(emailService, never()).sendHtml(any(), any(), any(), any())
        verify(passwordResetTokenRepository, never()).save(any())
    }

    @Test
    fun `forgotPassword does nothing when user does not exist`() {
        whenever(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("unknown@example.com")).thenReturn(null)

        val response = service.forgotPassword(ForgotPasswordRequest(email = "unknown@example.com"), "127.0.0.1")

        assertEquals("If the email is registered, reset instructions have been sent.", response.message)
        verify(passwordEncoder, never()).encode(any())
        verify(userRepository, never()).save(any())
        verify(emailService, never()).sendHtml(any(), any(), any(), any())
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
        verify(passwordResetTokenRepository, never()).registerFailedAttempt(any(), any(), any())
    }

    @Test
    fun `resetPassword throws when token is invalid and records failed attempt`() {
        whenever(passwordResetTokenRepository.findActiveByTokenHash(any(), any())).thenReturn(null)

        val ex = assertThrows(BadRequestApiException::class.java) {
            service.resetPassword(
                ResetPasswordRequest(
                    token = "S7A6B8NINzV0vDgCk3iybB24wL-r2I8M7VJt1o8C2aM",
                    newPassword = "New@Password1"
                )
            )
        }

        assertNotNull(ex)
        verify(userRepository, never()).save(any())
        verify(passwordResetTokenRepository, times(1)).registerFailedAttempt(any(), any(), eq(5))
    }

    @Test
    fun `tokenFingerprint is a stable hash for rate limiting without exposing token`() {
        val token = "S7A6B8NINzV0vDgCk3iybB24wL-r2I8M7VJt1o8C2aM"

        val fingerprint = service.tokenFingerprint(token)

        assertEquals(64, fingerprint.length)
        assertEquals(fingerprint, service.tokenFingerprint(token))
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
