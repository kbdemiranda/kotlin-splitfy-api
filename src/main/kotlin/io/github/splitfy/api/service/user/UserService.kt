package io.github.splitfy.api.service.user

import io.github.splitfy.api.domain.entity.User
import io.github.splitfy.api.domain.entity.Profile
import io.github.splitfy.api.domain.enums.ProfileName
import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.exception.ConflictApiException
import io.github.splitfy.api.exception.ResourceNotFoundApiException
import io.github.splitfy.api.logging.infoEvent
import io.github.splitfy.api.repository.UserRepository
import io.github.splitfy.api.service.email.EmailService
import io.github.splitfy.api.service.email.EmailTemplateService
import io.github.splitfy.api.service.profile.ProfileService
import io.github.splitfy.api.web.user.dto.ProfileSummaryResponse
import io.github.splitfy.api.web.user.dto.UserCreateRequest
import io.github.splitfy.api.web.user.dto.UserDashboardEmailPreferenceRequest
import io.github.splitfy.api.web.user.dto.UserResponse
import io.github.splitfy.api.web.user.dto.UserUpdateRequest
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.slf4j.LoggerFactory
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
    private val emailTemplateService: EmailTemplateService,
    private val passwordEncoder: PasswordEncoder,
) {
    private val log = LoggerFactory.getLogger(UserService::class.java)

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
        log.infoEvent("crud.create", "entity" to "user", "entityId" to saved.id, "email" to saved.email, "profileId" to saved.profile?.id)
        return toResponse(saved)
    }

    fun list(pageable: Pageable, name: String?): Page<UserResponse> {
        val users = if (name.isNullOrBlank()) {
            userRepository.findByDeletedAtIsNull(pageable)
        } else {
            userRepository.findByDeletedAtIsNullAndNameContainingIgnoreCase(name, pageable)
        }

        log.infoEvent("crud.list", "entity" to "user", "page" to pageable.pageNumber, "size" to pageable.pageSize, "filterName" to name, "resultCount" to users.numberOfElements)
        return users.map(::toResponse)
    }

    fun getById(id: UUID): UserResponse {
        val user = getActiveUser(id)
        log.infoEvent("crud.get", "entity" to "user", "entityId" to user.id, "email" to user.email, "profileId" to user.profile?.id)
        return toResponse(user)
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
        log.infoEvent("crud.update", "entity" to "user", "entityId" to saved.id, "email" to saved.email, "profileId" to saved.profile?.id, "isEnabled" to saved.isEnabled)
        return toResponse(saved)
    }

    fun updateDashboardEmailPreference(id: UUID, request: UserDashboardEmailPreferenceRequest): UserResponse {
        val user = getActiveUser(id)

        if (request.receivesDashboardEmail) {
            if (!user.isEnabled) {
                throw BadRequestApiException("User must be enabled to receive scheduled dashboard e-mail")
            }

            val currentRecipient = userRepository.findByReceivesDashboardEmailTrueAndDeletedAtIsNullAndIsEnabledTrue()
            if (currentRecipient != null && currentRecipient.id != user.id) {
                if (!request.force) {
                    throw ConflictApiException(
                        "Another user is already configured as the scheduled dashboard e-mail recipient: " +
                            "${currentRecipient.name} <${currentRecipient.email}>. Retry with force=true to replace it."
                    )
                }

                currentRecipient.receivesDashboardEmail = false
                userRepository.save(currentRecipient)
            }
        }

        user.receivesDashboardEmail = request.receivesDashboardEmail
        val saved = userRepository.save(user)
        log.infoEvent("user.dashboard-email-preference.updated", "entity" to "user", "entityId" to saved.id, "email" to saved.email, "receivesDashboardEmail" to saved.receivesDashboardEmail)
        return toResponse(saved)
    }

    fun softDelete(id: UUID) {
        val user = getActiveUser(id)
        user.isEnabled = false
        user.receivesDashboardEmail = false
        user.deletedAt = LocalDateTime.now()
        val saved = userRepository.save(user)
        log.infoEvent("crud.delete", "entity" to "user", "entityId" to saved.id, "email" to saved.email)
    }

    fun associateProfile(id: UUID, profileId: UUID): UserResponse {
        val user = getActiveUser(id)
        user.profile = profileService.getProfile(profileId)
        val saved = userRepository.save(user)
        log.infoEvent("user.profile.associated", "entity" to "user", "entityId" to saved.id, "profileId" to saved.profile?.id, "email" to saved.email)
        return toResponse(saved)
    }

    fun disassociateProfile(id: UUID): UserResponse {
        val user = getActiveUser(id)
        user.profile = null
        val saved = userRepository.save(user)
        log.infoEvent("user.profile.disassociated", "entity" to "user", "entityId" to saved.id, "email" to saved.email)
        return toResponse(saved)
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
            receivesDashboardEmail = user.receivesDashboardEmail,
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
        val htmlBody = emailTemplateService.renderTemplate(
            templateName = "email/user-welcome",
            variables = mapOf(
                "preheader" to "Seu acesso ao Splitfy foi criado com sucesso",
                "name" to user.name,
                "email" to user.email
            )
        )

        emailService.sendHtml(
            to = user.email,
            subject = subject,
            htmlBody = htmlBody,
        )
        log.infoEvent("email.user.welcome.sent", "entity" to "user", "entityId" to user.id, "email" to user.email)
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
