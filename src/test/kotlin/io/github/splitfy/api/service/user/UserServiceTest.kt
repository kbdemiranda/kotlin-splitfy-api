package io.github.splitfy.api.service.user

import io.github.splitfy.api.domain.entity.Profile
import io.github.splitfy.api.domain.entity.User
import io.github.splitfy.api.domain.enums.ProfileName
import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.exception.ConflictApiException
import io.github.splitfy.api.repository.UserRepository
import io.github.splitfy.api.service.email.EmailService
import io.github.splitfy.api.service.email.EmailTemplateService
import io.github.splitfy.api.service.profile.ProfileService
import io.github.splitfy.api.web.user.dto.UserCreateRequest
import io.github.splitfy.api.web.user.dto.UserDashboardEmailPreferenceRequest
import io.github.splitfy.api.web.user.dto.UserUpdateRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserServiceTest {

    private val userRepository: UserRepository = mock()
    private val profileService: ProfileService = mock()
    private val emailService: EmailService = mock()
    private val emailTemplateService = EmailTemplateService()
    private val passwordEncoder: PasswordEncoder = mock()

    private val service = UserService(
        userRepository = userRepository,
        profileService = profileService,
        emailService = emailService,
        emailTemplateService = emailTemplateService,
        passwordEncoder = passwordEncoder,
    )

    @Test
    fun `create no longer changes dashboard email recipient`() {
        val profile = sampleProfile()

        whenever(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("new@splitfy.com")).thenReturn(false)
        whenever(profileService.getProfileByName(ProfileName.VIEWER)).thenReturn(profile)
        whenever(passwordEncoder.encode("password123")).thenReturn("encoded-password")
        whenever(userRepository.save(any())).thenAnswer { invocation ->
            val saved = invocation.getArgument<User>(0)
            if (saved.id == null) {
                saved.id = UUID.randomUUID()
            }
            saved
        }

        service.create(
            UserCreateRequest(
                name = "New User",
                email = "new@splitfy.com",
                password = "password123",
            )
        )

        verify(userRepository, times(1)).save(any())
        verify(userRepository).save(argThat<User> { email == "new@splitfy.com" && !receivesDashboardEmail })
    }

    @Test
    fun `create ignores privileged profile sent by client and always uses viewer`() {
        val viewerProfile = sampleProfile()

        whenever(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("new@splitfy.com")).thenReturn(false)
        whenever(profileService.getProfileByName(ProfileName.VIEWER)).thenReturn(viewerProfile)
        whenever(passwordEncoder.encode("password123")).thenReturn("encoded-password")
        whenever(userRepository.save(any())).thenAnswer { invocation ->
            val saved = invocation.getArgument<User>(0)
            if (saved.id == null) {
                saved.id = UUID.randomUUID()
            }
            saved
        }

        service.create(
            UserCreateRequest(
                name = "New User",
                email = "new@splitfy.com",
                password = "password123",
                profileName = ProfileName.ADMIN,
            )
        )

        verify(profileService, times(1)).getProfileByName(ProfileName.VIEWER)
        verify(profileService, never()).getProfileByName(ProfileName.ADMIN)
        verify(userRepository).save(argThat<User> { profile?.name == ProfileName.VIEWER })
    }

    @Test
    fun `update no longer changes dashboard email recipient`() {
        val targetUserId = UUID.randomUUID()
        val targetUser = sampleUser(
            id = targetUserId,
            email = "target@splitfy.com",
            receivesDashboardEmail = false
        )

        whenever(userRepository.findByIdAndDeletedAtIsNull(targetUserId)).thenReturn(targetUser)
        whenever(userRepository.save(any())).thenAnswer { invocation -> invocation.getArgument(0) }

        service.update(
            targetUserId,
            UserUpdateRequest(name = "Updated User")
        )

        assertFalse(targetUser.receivesDashboardEmail)
        verify(userRepository, times(1)).save(any())
    }

    @Test
    fun `updateDashboardEmailPreference rejects selecting disabled user as recipient`() {
        val userId = UUID.randomUUID()
        val user = sampleUser(
            id = userId,
            email = "disabled@splitfy.com",
            isEnabled = false,
            receivesDashboardEmail = false
        )

        whenever(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(user)

        assertThrows<BadRequestApiException> {
            service.updateDashboardEmailPreference(
                userId,
                UserDashboardEmailPreferenceRequest(receivesDashboardEmail = true)
            )
        }

        verify(userRepository, never()).save(any())
    }

    @Test
    fun `updateDashboardEmailPreference returns conflict when another user is already selected and force is false`() {
        val userId = UUID.randomUUID()
        val currentRecipient = sampleUser(
            id = UUID.randomUUID(),
            email = "current@splitfy.com",
            receivesDashboardEmail = true
        )
        val targetUser = sampleUser(
            id = userId,
            email = "target@splitfy.com",
            receivesDashboardEmail = false
        )

        whenever(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(targetUser)
        whenever(userRepository.findByReceivesDashboardEmailTrueAndDeletedAtIsNullAndIsEnabledTrue()).thenReturn(currentRecipient)

        assertThrows<ConflictApiException> {
            service.updateDashboardEmailPreference(
                userId,
                UserDashboardEmailPreferenceRequest(receivesDashboardEmail = true, force = false)
            )
        }

        assertTrue(currentRecipient.receivesDashboardEmail)
        assertFalse(targetUser.receivesDashboardEmail)
        verify(userRepository, never()).save(argThat<User> { id == currentRecipient.id && !receivesDashboardEmail })
    }

    @Test
    fun `updateDashboardEmailPreference replaces current recipient when force is true`() {
        val userId = UUID.randomUUID()
        val currentRecipient = sampleUser(
            id = UUID.randomUUID(),
            email = "current@splitfy.com",
            receivesDashboardEmail = true
        )
        val targetUser = sampleUser(
            id = userId,
            email = "target@splitfy.com",
            receivesDashboardEmail = false
        )

        whenever(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(targetUser)
        whenever(userRepository.findByReceivesDashboardEmailTrueAndDeletedAtIsNullAndIsEnabledTrue()).thenReturn(currentRecipient)
        whenever(userRepository.save(any())).thenAnswer { invocation -> invocation.getArgument(0) }

        service.updateDashboardEmailPreference(
            userId,
            UserDashboardEmailPreferenceRequest(receivesDashboardEmail = true, force = true)
        )

        assertFalse(currentRecipient.receivesDashboardEmail)
        assertTrue(targetUser.receivesDashboardEmail)
        verify(userRepository, times(2)).save(any())
    }

    private fun sampleProfile(): Profile {
        return Profile(
            id = UUID.randomUUID(),
            name = ProfileName.VIEWER,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
    }

    private fun sampleUser(
        id: UUID,
        email: String,
        isEnabled: Boolean = true,
        receivesDashboardEmail: Boolean = false,
    ): User {
        return User(
            id = id,
            name = "User",
            email = email,
            password = "old-password",
            profile = null,
            isEnabled = isEnabled,
            receivesDashboardEmail = receivesDashboardEmail,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            deletedAt = null
        )
    }
}
