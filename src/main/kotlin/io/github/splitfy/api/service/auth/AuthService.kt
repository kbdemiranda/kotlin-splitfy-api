package io.github.splitfy.api.service.auth

import io.github.splitfy.api.exception.UnauthorizedApiException
import io.github.splitfy.api.repository.UserRepository
import io.github.splitfy.api.service.email.EmailService
import io.github.splitfy.api.security.JwtProperties
import io.github.splitfy.api.security.JwtService
import io.github.splitfy.api.security.TokenBlacklistService
import io.github.splitfy.api.web.auth.dto.ForgotPasswordRequest
import io.github.splitfy.api.web.auth.dto.LoginRequest
import io.github.splitfy.api.web.auth.dto.LoginResponse
import io.github.splitfy.api.web.auth.dto.SimpleMessageResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.security.SecureRandom

@Service
class AuthService(
    private val authenticationManager: AuthenticationManager,
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val tokenBlacklistService: TokenBlacklistService,
    private val jwtProperties: JwtProperties,
    private val passwordEncoder: PasswordEncoder,
    private val emailService: EmailService,
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

    fun forgotPassword(request: ForgotPasswordRequest): SimpleMessageResponse {
        val normalizedEmail = request.email.lowercase()
        val user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail)

        if (user != null && user.isEnabled) {
            val temporaryPassword = generateTemporaryPassword()
            user.password = passwordEncoder.encode(temporaryPassword)
                ?: throw IllegalStateException("Password encoding failed")
            userRepository.save(user)
            sendTemporaryPasswordEmail(user.email, user.name, temporaryPassword)
        }

        return SimpleMessageResponse("If the email is registered, a new password has been sent.")
    }

    private fun generateTemporaryPassword(length: Int = 12): String {
        val lowercase = "abcdefghijkmnopqrstuvwxyz"
        val uppercase = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val digits = "23456789"
        val symbols = "@#%&*!"
        val allChars = lowercase + uppercase + digits + symbols
        val random = SecureRandom()

        val mandatory = mutableListOf(
            pickRandomChar(lowercase, random),
            pickRandomChar(uppercase, random),
            pickRandomChar(digits, random),
            pickRandomChar(symbols, random),
        )

        repeat(length - mandatory.size) {
            mandatory.add(pickRandomChar(allChars, random))
        }

        return mandatory.shuffled().joinToString("")
    }

    private fun pickRandomChar(charset: String, random: SecureRandom): Char {
        return charset[random.nextInt(charset.length)]
    }

    private fun sendTemporaryPasswordEmail(email: String, name: String, temporaryPassword: String) {
        val subject = "Splitfy - Nova senha temporaria"
        val htmlBody = """
            <html>
              <body>
                <h2>Ola, $name!</h2>
                <p>Recebemos uma solicitacao de redefinicao de senha para sua conta.</p>
                <p>Sua nova senha temporaria e: <strong>$temporaryPassword</strong></p>
                <p>Recomendamos alterar essa senha apos o proximo login.</p>
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
