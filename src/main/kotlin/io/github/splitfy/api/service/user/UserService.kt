package io.github.splitfy.api.service.user

import io.github.splitfy.api.domain.entity.User
import io.github.splitfy.api.domain.entity.Profile
import io.github.splitfy.api.domain.enums.ProfileName
import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.exception.ConflictApiException
import io.github.splitfy.api.exception.ResourceNotFoundApiException
import io.github.splitfy.api.repository.UserRepository
import io.github.splitfy.api.service.email.EmailService
import io.github.splitfy.api.service.profile.ProfileService
import io.github.splitfy.api.web.user.dto.ProfileSummaryResponse
import io.github.splitfy.api.web.user.dto.UserCreateRequest
import io.github.splitfy.api.web.user.dto.UserResponse
import io.github.splitfy.api.web.user.dto.UserUpdateRequest
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
    private val profileService: ProfileService,
    private val emailService: EmailService,
    private val passwordEncoder: PasswordEncoder,
) {

    fun create(request: UserCreateRequest): UserResponse {
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(request.email)) {
            throw ConflictApiException("Email already in use: ${request.email}")
        }

        val profile = resolveProfileForCreate(request)
        val user = User(
            name = request.name,
            email = request.email.lowercase(),
            password = encodePassword(request.password),
            profile = profile,
            isEnabled = request.isEnabled,
        )

        val saved = userRepository.save(user)
        sendWelcomeEmail(saved)
        return toResponse(saved)
    }

    fun list(pageable: Pageable, name: String?): Page<UserResponse> {
        val users = if (name.isNullOrBlank()) {
            userRepository.findByDeletedAtIsNull(pageable)
        } else {
            userRepository.findByDeletedAtIsNullAndNameContainingIgnoreCase(name, pageable)
        }

        return users.map(::toResponse)
    }

    fun getById(id: UUID): UserResponse {
        return toResponse(getActiveUser(id))
    }

    fun update(id: UUID, request: UserUpdateRequest): UserResponse {
        val user = getActiveUser(id)

        val nextEmail = request.email?.lowercase()
        if (!nextEmail.isNullOrBlank() && nextEmail != user.email &&
            userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNullAndIdNot(nextEmail, id)
        ) {
            throw ConflictApiException("Email already in use: $nextEmail")
        }

        request.name?.let { user.name = it }
        nextEmail?.let { user.email = it }
        request.password?.let { user.password = encodePassword(it) }
        val profile = resolveProfileForUpdate(request)
        profile?.let { user.profile = it }
        request.isEnabled?.let { user.isEnabled = it }

        val saved = userRepository.save(user)
        return toResponse(saved)
    }

    fun softDelete(id: UUID) {
        val user = getActiveUser(id)
        user.isEnabled = false
        user.deletedAt = LocalDateTime.now()
        userRepository.save(user)
    }

    fun associateProfile(id: UUID, profileId: UUID): UserResponse {
        val user = getActiveUser(id)
        user.profile = profileService.getProfile(profileId)
        return toResponse(userRepository.save(user))
    }

    fun disassociateProfile(id: UUID): UserResponse {
        val user = getActiveUser(id)
        user.profile = null
        return toResponse(userRepository.save(user))
    }

    private fun getActiveUser(id: UUID): User {
        return userRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw ResourceNotFoundApiException("User not found with id: $id")
    }

    private fun toResponse(user: User): UserResponse {
        val userId = user.id ?: throw IllegalStateException("User ID must not be null")

        val profileSummary = user.profile?.let {
            val profileId = it.id ?: throw IllegalStateException("Profile ID must not be null")
            ProfileSummaryResponse(id = profileId, name = it.name)
        }

        return UserResponse(
            id = userId,
            name = user.name,
            email = user.email,
            profile = profileSummary,
            isEnabled = user.isEnabled,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt,
            deletedAt = user.deletedAt,
        )
    }

    private fun encodePassword(rawPassword: String): String {
        return passwordEncoder.encode(rawPassword)
            ?: throw IllegalStateException("Password encoding failed")
    }

    private fun sendWelcomeEmail(user: User) {
        val subject = "Bem-vindo ao Splitfy"
        val htmlBody = """
            <html>
              <body>
                <h2>Bem-vindo(a), ${user.name}!</h2>
                <p>Seu usuário foi criado com sucesso no Splitfy.</p>
                <p>Agora você já pode acessar a plataforma com o e-mail <strong>${user.email}</strong>.</p>
              </body>
            </html>
        """.trimIndent()

        emailService.sendHtml(
            to = user.email,
            subject = subject,
            htmlBody = htmlBody,
        )
    }

    private fun resolveProfileForCreate(request: UserCreateRequest): Profile {
        if (request.profileId != null && request.profileName != null) {
            throw BadRequestApiException("Provide either profileId or profileName, not both")
        }

        request.profileId?.let { return profileService.getProfile(it) }
        request.profileName?.let { return profileService.getProfileByName(it) }

        return profileService.getProfileByName(ProfileName.VIEWER)
    }

    private fun resolveProfileForUpdate(request: UserUpdateRequest): Profile? {
        if (request.profileId != null && request.profileName != null) {
            throw BadRequestApiException("Provide either profileId or profileName, not both")
        }

        request.profileId?.let { return profileService.getProfile(it) }
        request.profileName?.let { return profileService.getProfileByName(it) }

        return null
    }
}
