package io.github.splitfy.api.service.profile

import io.github.splitfy.api.domain.entity.Profile
import io.github.splitfy.api.domain.enums.ProfileName
import io.github.splitfy.api.exception.ConflictApiException
import io.github.splitfy.api.exception.ResourceNotFoundApiException
import io.github.splitfy.api.logging.infoEvent
import io.github.splitfy.api.repository.ProfileRepository
import io.github.splitfy.api.repository.UserRepository
import io.github.splitfy.api.web.profile.dto.ProfileRequest
import io.github.splitfy.api.web.profile.dto.ProfileResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class ProfileService(
    private val profileRepository: ProfileRepository,
    private val userRepository: UserRepository,
) {
    private val log = LoggerFactory.getLogger(ProfileService::class.java)

    fun create(request: ProfileRequest): ProfileResponse {
        if (profileRepository.existsByName(request.name)) {
            throw ConflictApiException("Profile already exists for name: ${request.name}")
        }

        val profile = profileRepository.save(Profile(name = request.name))
        log.infoEvent("crud.create", "entity" to "profile", "entityId" to profile.id, "profileName" to profile.name)
        return toResponse(profile)
    }

    fun list(pageable: Pageable): Page<ProfileResponse> {
        val profiles = profileRepository.findAll(pageable)
        log.infoEvent("crud.list", "entity" to "profile", "page" to pageable.pageNumber, "size" to pageable.pageSize, "resultCount" to profiles.numberOfElements)
        return profiles.map(::toResponse)
    }

    fun getById(id: UUID): ProfileResponse {
        val profile = getProfile(id)
        log.infoEvent("crud.get", "entity" to "profile", "entityId" to profile.id, "profileName" to profile.name)
        return toResponse(profile)
    }

    fun update(id: UUID, request: ProfileRequest): ProfileResponse {
        val existing = getProfile(id)
        if (existing.name != request.name && profileRepository.existsByName(request.name)) {
            throw ConflictApiException("Profile already exists for name: ${request.name}")
        }

        existing.name = request.name
        val saved = profileRepository.save(existing)
        log.infoEvent("crud.update", "entity" to "profile", "entityId" to saved.id, "profileName" to saved.name)
        return toResponse(saved)
    }

    fun delete(id: UUID) {
        if (userRepository.existsByProfileIdAndDeletedAtIsNull(id)) {
            throw ConflictApiException("Profile is associated with active users and cannot be deleted")
        }

        val profile = getProfile(id)
        profileRepository.delete(profile)
        log.infoEvent("crud.delete", "entity" to "profile", "entityId" to profile.id, "profileName" to profile.name)
    }

    fun getProfile(id: UUID): Profile {
        return profileRepository.findById(id)
            .orElseThrow { ResourceNotFoundApiException("Profile not found with id: $id") }
    }

    fun getProfileByName(name: ProfileName): Profile {
        return profileRepository.findByName(name)
            ?: throw ResourceNotFoundApiException("Profile not found with name: $name")
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
