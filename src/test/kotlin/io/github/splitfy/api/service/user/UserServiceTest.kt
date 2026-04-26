package io.github.splitfy.api.service.user

import io.github.splitfy.api.domain.entity.Profile
import io.github.splitfy.api.domain.entity.User
import io.github.splitfy.api.domain.enums.ProfileName
import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.exception.ConflictApiException
import io.github.splitfy.api.exception.ResourceNotFoundApiException
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
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
    fun `create always uses viewer profile`() {
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
            )
        )

        verify(profileService, times(1)).getProfileByName(ProfileName.VIEWER)
        verify(userRepository).save(argThat<User> { profile?.name == ProfileName.VIEWER })
    }

    @Test
    fun `create lowercases email before saving`() {
        val viewerProfile = sampleProfile()

        whenever(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("Mixed@Splitfy.COM")).thenReturn(false)
        whenever(profileService.getProfileByName(ProfileName.VIEWER)).thenReturn(viewerProfile)
        whenever(passwordEncoder.encode("password123")).thenReturn("encoded-password")
        whenever(userRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<User>(0).apply {
                id = UUID.randomUUID()
                createdAt = LocalDateTime.now()
                updatedAt = createdAt
            }
        }

        val response = service.create(
            UserCreateRequest(
                name = "New User",
                email = "Mixed@Splitfy.COM",
                password = "password123",
            )
        )

        assertEquals("mixed@splitfy.com", response.email)
        verify(userRepository).save(argThat<User> { email == "mixed@splitfy.com" })
    }

    @Test
    fun `create throws conflict when active user already has email`() {
        whenever(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("new@splitfy.com")).thenReturn(true)

        assertThrows<ConflictApiException> {
            service.create(
                UserCreateRequest(
                    name = "New User",
                    email = "new@splitfy.com",
                    password = "password123",
                )
            )
        }

        verify(userRepository, never()).save(any())
        verify(profileService, never()).getProfileByName(any())
    }

    @Test
    fun `list returns unfiltered active users`() {
        val pageable = PageRequest.of(0, 10)
        val user = sampleUser(UUID.randomUUID(), "user@splitfy.com")
        whenever(userRepository.findByDeletedAtIsNull(pageable)).thenReturn(PageImpl(listOf(user), pageable, 1))

        val response = service.list(pageable, null)

        assertEquals(1, response.totalElements)
        assertEquals("user@splitfy.com", response.content.first().email)
        verify(userRepository).findByDeletedAtIsNull(pageable)
        verify(userRepository, never()).findByDeletedAtIsNullAndNameContainingIgnoreCase(any(), any())
    }

    @Test
    fun `list returns filtered active users by name`() {
        val pageable = PageRequest.of(0, 10)
        val user = sampleUser(UUID.randomUUID(), "ana@splitfy.com")
        whenever(userRepository.findByDeletedAtIsNullAndNameContainingIgnoreCase("Ana", pageable))
            .thenReturn(PageImpl(listOf(user), pageable, 1))

        val response = service.list(pageable, "Ana")

        assertEquals(1, response.totalElements)
        verify(userRepository).findByDeletedAtIsNullAndNameContainingIgnoreCase("Ana", pageable)
    }

    @Test
    fun `getById throws when active user does not exist`() {
        val userId = UUID.randomUUID()
        whenever(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(null)

        assertThrows<ResourceNotFoundApiException> {
            service.getById(userId)
        }
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
    fun `update changes optional fields and profile by id`() {
        val userId = UUID.randomUUID()
        val profileId = UUID.randomUUID()
        val profile = sampleProfile(ProfileName.EDITOR, profileId)
        val targetUser = sampleUser(
            id = userId,
            email = "old@splitfy.com",
            isEnabled = false
        )

        whenever(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(targetUser)
        whenever(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNullAndIdNot("new@splitfy.com", userId)).thenReturn(false)
        whenever(passwordEncoder.encode("new-password")).thenReturn("encoded-new-password")
        whenever(profileService.getProfile(profileId)).thenReturn(profile)
        whenever(userRepository.save(any())).thenAnswer { invocation -> invocation.getArgument(0) }

        val response = service.update(
            userId,
            UserUpdateRequest(
                name = "Updated",
                email = "NEW@Splitfy.COM",
                password = "new-password",
                profileId = profileId,
                isEnabled = true
            )
        )

        assertEquals("Updated", response.name)
        assertEquals("new@splitfy.com", response.email)
        assertEquals(ProfileName.EDITOR, response.profile?.name)
        assertTrue(response.isEnabled)
        assertEquals("encoded-new-password", targetUser.password)
    }

    @Test
    fun `update changes profile by name`() {
        val userId = UUID.randomUUID()
        val profile = sampleProfile(ProfileName.ADMIN)
        val targetUser = sampleUser(id = userId, email = "target@splitfy.com")

        whenever(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(targetUser)
        whenever(profileService.getProfileByName(ProfileName.ADMIN)).thenReturn(profile)
        whenever(userRepository.save(any())).thenAnswer { invocation -> invocation.getArgument(0) }

        val response = service.update(userId, UserUpdateRequest(profileName = ProfileName.ADMIN))

        assertEquals(ProfileName.ADMIN, response.profile?.name)
        verify(profileService).getProfileByName(ProfileName.ADMIN)
    }

    @Test
    fun `update throws conflict when changing to email used by another active user`() {
        val userId = UUID.randomUUID()
        val targetUser = sampleUser(id = userId, email = "old@splitfy.com")

        whenever(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(targetUser)
        whenever(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNullAndIdNot("used@splitfy.com", userId)).thenReturn(true)

        assertThrows<ConflictApiException> {
            service.update(userId, UserUpdateRequest(email = "used@splitfy.com"))
        }

        verify(userRepository, never()).save(any())
    }

    @Test
    fun `update rejects profile id and profile name together`() {
        val userId = UUID.randomUUID()
        whenever(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(sampleUser(userId, "user@splitfy.com"))

        assertThrows<BadRequestApiException> {
            service.update(
                userId,
                UserUpdateRequest(profileId = UUID.randomUUID(), profileName = ProfileName.ADMIN)
            )
        }

        verify(userRepository, never()).save(any())
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

    @Test
    fun `softDelete disables user clears dashboard recipient and sets deletion time`() {
        val userId = UUID.randomUUID()
        val targetUser = sampleUser(
            id = userId,
            email = "target@splitfy.com",
            receivesDashboardEmail = true
        )
        whenever(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(targetUser)
        whenever(userRepository.save(any())).thenAnswer { invocation -> invocation.getArgument(0) }

        service.softDelete(userId)

        assertFalse(targetUser.isEnabled)
        assertFalse(targetUser.receivesDashboardEmail)
        assertTrue(targetUser.deletedAt != null)
        verify(userRepository).save(targetUser)
    }

    @Test
    fun `associateProfile assigns requested profile`() {
        val userId = UUID.randomUUID()
        val profile = sampleProfile(ProfileName.ADMIN)
        val targetUser = sampleUser(id = userId, email = "target@splitfy.com")

        whenever(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(targetUser)
        whenever(profileService.getProfile(eq(profile.id!!))).thenReturn(profile)
        whenever(userRepository.save(any())).thenAnswer { invocation -> invocation.getArgument(0) }

        val response = service.associateProfile(userId, profile.id!!)

        assertEquals(ProfileName.ADMIN, response.profile?.name)
        verify(userRepository).save(argThat<User> { this.profile == profile })
    }

    @Test
    fun `disassociateProfile clears profile`() {
        val userId = UUID.randomUUID()
        val targetUser = sampleUser(
            id = userId,
            email = "target@splitfy.com"
        ).apply {
            profile = sampleProfile(ProfileName.EDITOR)
        }

        whenever(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(targetUser)
        whenever(userRepository.save(any())).thenAnswer { invocation -> invocation.getArgument(0) }

        val response = service.disassociateProfile(userId)

        assertEquals(null, response.profile)
        verify(userRepository).save(argThat<User> { profile == null })
    }

    private fun sampleProfile(
        name: ProfileName = ProfileName.VIEWER,
        id: UUID = UUID.randomUUID()
    ): Profile {
        return Profile(
            id = id,
            name = name,
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
