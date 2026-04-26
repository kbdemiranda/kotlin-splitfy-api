package io.github.splitfy.api.service.profile

import io.github.splitfy.api.domain.entity.Profile
import io.github.splitfy.api.domain.enums.ProfileName
import io.github.splitfy.api.exception.ConflictApiException
import io.github.splitfy.api.exception.ResourceNotFoundApiException
import io.github.splitfy.api.repository.ProfileRepository
import io.github.splitfy.api.repository.UserRepository
import io.github.splitfy.api.web.profile.dto.ProfileRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ProfileServiceTest {

    @Mock
    lateinit var profileRepository: ProfileRepository

    @Mock
    lateinit var userRepository: UserRepository

    private val service by lazy {
        ProfileService(profileRepository, userRepository)
    }

    @Test
    fun `create saves profile when name is available`() {
        whenever(profileRepository.existsByName(ProfileName.EDITOR)).thenReturn(false)
        whenever(profileRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<Profile>(0).apply {
                id = UUID.randomUUID()
                createdAt = LocalDateTime.of(2026, 4, 1, 10, 0)
                updatedAt = createdAt
            }
        }

        val response = service.create(ProfileRequest(ProfileName.EDITOR))

        assertNotNull(response.id)
        assertEquals(ProfileName.EDITOR, response.name)
        val captor = argumentCaptor<Profile>()
        verify(profileRepository).save(captor.capture())
        assertEquals(ProfileName.EDITOR, captor.firstValue.name)
    }

    @Test
    fun `create throws conflict when name already exists`() {
        whenever(profileRepository.existsByName(ProfileName.ADMIN)).thenReturn(true)

        assertThrows(ConflictApiException::class.java) {
            service.create(ProfileRequest(ProfileName.ADMIN))
        }

        verify(profileRepository, never()).save(any())
    }

    @Test
    fun `list maps profiles to responses`() {
        val pageable = PageRequest.of(0, 2)
        val profiles = PageImpl(
            listOf(sampleProfile(ProfileName.ADMIN), sampleProfile(ProfileName.VIEWER)),
            pageable,
            2
        )
        whenever(profileRepository.findAll(pageable)).thenReturn(profiles)

        val response = service.list(pageable)

        assertEquals(2, response.totalElements)
        assertEquals(listOf(ProfileName.ADMIN, ProfileName.VIEWER), response.content.map { it.name })
    }

    @Test
    fun `getById returns existing profile`() {
        val id = UUID.randomUUID()
        whenever(profileRepository.findById(id)).thenReturn(Optional.of(sampleProfile(ProfileName.VIEWER, id)))

        val response = service.getById(id)

        assertEquals(id, response.id)
        assertEquals(ProfileName.VIEWER, response.name)
    }

    @Test
    fun `getById throws when profile does not exist`() {
        val id = UUID.randomUUID()
        whenever(profileRepository.findById(id)).thenReturn(Optional.empty())

        assertThrows(ResourceNotFoundApiException::class.java) {
            service.getById(id)
        }
    }

    @Test
    fun `update changes name when target name is available`() {
        val id = UUID.randomUUID()
        val existing = sampleProfile(ProfileName.VIEWER, id)
        whenever(profileRepository.findById(id)).thenReturn(Optional.of(existing))
        whenever(profileRepository.existsByName(ProfileName.EDITOR)).thenReturn(false)
        whenever(profileRepository.save(any())).thenAnswer { invocation -> invocation.getArgument(0) }

        val response = service.update(id, ProfileRequest(ProfileName.EDITOR))

        assertEquals(ProfileName.EDITOR, response.name)
        verify(profileRepository).save(existing)
    }

    @Test
    fun `update keeps same name without duplicate lookup`() {
        val id = UUID.randomUUID()
        val existing = sampleProfile(ProfileName.ADMIN, id)
        whenever(profileRepository.findById(id)).thenReturn(Optional.of(existing))
        whenever(profileRepository.save(any())).thenAnswer { invocation -> invocation.getArgument(0) }

        service.update(id, ProfileRequest(ProfileName.ADMIN))

        verify(profileRepository, never()).existsByName(any())
        verify(profileRepository).save(existing)
    }

    @Test
    fun `update throws conflict when another profile has target name`() {
        val id = UUID.randomUUID()
        whenever(profileRepository.findById(id)).thenReturn(Optional.of(sampleProfile(ProfileName.VIEWER, id)))
        whenever(profileRepository.existsByName(ProfileName.ADMIN)).thenReturn(true)

        assertThrows(ConflictApiException::class.java) {
            service.update(id, ProfileRequest(ProfileName.ADMIN))
        }

        verify(profileRepository, never()).save(any())
    }

    @Test
    fun `delete removes unused profile`() {
        val id = UUID.randomUUID()
        val profile = sampleProfile(ProfileName.EDITOR, id)
        whenever(userRepository.existsByProfileIdAndDeletedAtIsNull(id)).thenReturn(false)
        whenever(profileRepository.findById(id)).thenReturn(Optional.of(profile))

        service.delete(id)

        verify(profileRepository).delete(profile)
    }

    @Test
    fun `delete throws conflict when active users reference profile`() {
        val id = UUID.randomUUID()
        whenever(userRepository.existsByProfileIdAndDeletedAtIsNull(id)).thenReturn(true)

        assertThrows(ConflictApiException::class.java) {
            service.delete(id)
        }

        verify(profileRepository, never()).delete(any())
    }

    @Test
    fun `getProfileByName returns profile by enum name`() {
        val profile = sampleProfile(ProfileName.ADMIN)
        whenever(profileRepository.findByName(ProfileName.ADMIN)).thenReturn(profile)

        val result = service.getProfileByName(ProfileName.ADMIN)

        assertEquals(profile, result)
    }

    @Test
    fun `getProfileByName throws when profile name is missing`() {
        whenever(profileRepository.findByName(ProfileName.EDITOR)).thenReturn(null)

        assertThrows(ResourceNotFoundApiException::class.java) {
            service.getProfileByName(ProfileName.EDITOR)
        }
    }

    private fun sampleProfile(
        name: ProfileName,
        id: UUID = UUID.randomUUID(),
    ): Profile {
        return Profile(
            id = id,
            name = name,
            createdAt = LocalDateTime.of(2026, 4, 1, 10, 0),
            updatedAt = LocalDateTime.of(2026, 4, 1, 10, 0),
        )
    }
}
