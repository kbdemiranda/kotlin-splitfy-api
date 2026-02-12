package io.github.splitfy.api.service.profile

import io.github.splitfy.api.domain.entity.Profile
import io.github.splitfy.api.domain.enums.ProfileName
import io.github.splitfy.api.repository.ProfileRepository
import io.github.splitfy.api.repository.UserRepository
import io.github.splitfy.api.web.profile.dto.ProfileRequest
import io.github.splitfy.api.web.profile.dto.ProfileResponse
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class ProfileService(
    private val profileRepository: ProfileRepository,
    private val userRepository: UserRepository,
) {

    fun create(request: ProfileRequest): ProfileResponse {
        if (profileRepository.existsByName(request.name)) {
            throw IllegalArgumentException("Profile already exists for name: ${request.name}")
        }

        val profile = profileRepository.save(Profile(name = request.name))
        return toResponse(profile)
    }

    fun list(pageable: Pageable): Page<ProfileResponse> {
        return profileRepository.findAll(pageable).map(::toResponse)
    }

    fun getById(id: UUID): ProfileResponse {
        return toResponse(getProfile(id))
    }

    fun update(id: UUID, request: ProfileRequest): ProfileResponse {
        val existing = getProfile(id)
        if (existing.name != request.name && profileRepository.existsByName(request.name)) {
            throw IllegalArgumentException("Profile already exists for name: ${request.name}")
        }

        existing.name = request.name
        val saved = profileRepository.save(existing)
        return toResponse(saved)
    }

    fun delete(id: UUID) {
        if (userRepository.existsByProfileIdAndDeletedAtIsNull(id)) {
            throw IllegalStateException("Profile is associated with active users and cannot be deleted")
        }

        val profile = getProfile(id)
        profileRepository.delete(profile)
    }

    fun getProfile(id: UUID): Profile {
        return profileRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Profile not found with id: $id") }
    }

    fun getProfileByName(name: ProfileName): Profile {
        return profileRepository.findByName(name)
            ?: throw EntityNotFoundException("Profile not found with name: $name")
    }

    private fun toResponse(profile: Profile): ProfileResponse {
        val id = profile.id ?: throw IllegalStateException("Profile ID must not be null")
        return ProfileResponse(
            id = id,
            name = profile.name,
            createdAt = profile.createdAt,
            updatedAt = profile.updatedAt,
        )
    }
}
