package io.github.splitfy.api.service.auth

import io.github.splitfy.api.domain.entity.PasswordResetToken
import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.repository.PasswordResetTokenRepository
import io.github.splitfy.api.exception.UnauthorizedApiException
import io.github.splitfy.api.repository.UserRepository
import io.github.splitfy.api.service.email.EmailService
import io.github.splitfy.api.security.JwtProperties
import io.github.splitfy.api.security.JwtService
import io.github.splitfy.api.security.TokenBlacklistService
import io.github.splitfy.api.web.auth.dto.ForgotPasswordRequest
import io.github.splitfy.api.web.auth.dto.LoginRequest
import io.github.splitfy.api.web.auth.dto.LoginResponse
import io.github.splitfy.api.web.auth.dto.ResetPasswordRequest
import io.github.splitfy.api.web.auth.dto.SimpleMessageResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime

@Service
class AuthService(
    private val authenticationManager: AuthenticationManager,
    private val userRepository: UserRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val jwtService: JwtService,
    private val tokenBlacklistService: TokenBlacklistService,
    private val jwtProperties: JwtProperties,
    private val passwordEncoder: PasswordEncoder,
    private val emailService: EmailService,
    @Value("\${splitfy.auth.reset-token-expiration-minutes:30}") private val resetTokenExpirationMinutes: Long,
) {

    fun login(request: LoginRequest): LoginResponse {
        runCatching {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(request.email, request.password),
            )
        }.onFailure {
            when (it) {
                is DisabledException -> throw UnauthorizedApiException(
                    message = "User is disabled",
                    code = "ACCOUNT_DISABLED"
                )
                else -> throw UnauthorizedApiException("Invalid email or password")
            }
        }

        val user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.email)
            ?: throw UnauthorizedApiException("Invalid email or password")

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

    @Transactional
    fun forgotPassword(request: ForgotPasswordRequest, requesterIp: String?): SimpleMessageResponse {
        val normalizedEmail = request.email.lowercase()
        val user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail)

        if (user != null && user.isEnabled) {
            val now = LocalDateTime.now()
            val rawToken = generateResetToken()
            val tokenHash = hashToken(rawToken)
            val expiresAt = now.plusMinutes(resetTokenExpirationMinutes)

            val userId = user.id ?: throw IllegalStateException("User ID must not be null")
            passwordResetTokenRepository.invalidateAllActiveByUserId(userId, now)
            passwordResetTokenRepository.save(
                PasswordResetToken(
                    user = user,
                    tokenHash = tokenHash,
                    expiresAt = expiresAt,
                    requestedIp = requesterIp?.take(64),
                )
            )

            sendResetPasswordEmail(user.email, user.name, rawToken, expiresAt)
        }

        return SimpleMessageResponse("If the email is registered, reset instructions have been sent.")
    }

    @Transactional
    fun resetPassword(request: ResetPasswordRequest): SimpleMessageResponse {
        val token = request.token.trim()
        if (token.isBlank()) {
            throw BadRequestApiException("Reset token is required")
        }

        val now = LocalDateTime.now()
        val tokenHash = hashToken(token)
        val resetToken = passwordResetTokenRepository.findActiveByTokenHash(tokenHash, now)
            ?: throw BadRequestApiException("Invalid or expired reset token")

        val user = resetToken.user
        if (!user.isEnabled || user.deletedAt != null) {
            throw BadRequestApiException("Invalid reset request")
        }

        user.password = passwordEncoder.encode(request.newPassword)
            ?: throw IllegalStateException("Password encoding failed")
        userRepository.save(user)

        val userId = user.id ?: throw IllegalStateException("User ID must not be null")
        passwordResetTokenRepository.invalidateAllActiveByUserId(userId, now)

        return SimpleMessageResponse("Password reset successful.")
    }

    private fun generateResetToken(): String {
        val code = SecureRandom().nextInt(1_000_000)
        return code.toString().padStart(6, '0')
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashedBytes = digest.digest(token.toByteArray(StandardCharsets.UTF_8))
        return hashedBytes.joinToString("") { "%02x".format(it) }
    }

    private fun sendResetPasswordEmail(
        email: String,
        name: String,
        resetToken: String,
        expiresAt: LocalDateTime
    ) {
        val subject = "Splitfy - redefinicao de senha"
        val htmlBody = """
            <html>
              <body>
                <h2>Ola, $name!</h2>
                <p>Recebemos uma solicitacao de redefinicao de senha para sua conta.</p>
                <p>Use o codigo abaixo para redefinir sua senha:</p>
                <p><strong style="font-size: 24px; letter-spacing: 2px;">$resetToken</strong></p>
                <p>Este token expira em: <strong>$expiresAt</strong></p>
                <p>Se voce nao solicitou, desconsidere este email.</p>
              </body>
            </html>
        """.trimIndent()

        emailService.sendHtml(
            to = email,
            subject = subject,
            htmlBody = htmlBody,
        )
    }
}
