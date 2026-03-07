package io.github.splitfy.api.bootstrap

import io.github.splitfy.api.domain.entity.Profile
import io.github.splitfy.api.domain.enums.ProfileName
import io.github.splitfy.api.repository.ProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DefaultProfileBootstrap(
    private val profileRepository: ProfileRepository,
) {

    private val logger = LoggerFactory.getLogger(DefaultProfileBootstrap::class.java)

    @Transactional
    fun ensureProfiles(): List<Profile> {
        val createdProfiles = mutableListOf<ProfileName>()

        for (profileName in ProfileName.entries) {
            if (!profileRepository.existsByName(profileName)) {
                profileRepository.save(Profile(name = profileName))
                createdProfiles.add(profileName)
            }
        }

        if (createdProfiles.isNotEmpty()) {
            logger.info("Created default profiles: {}", createdProfiles)
        } else {
            logger.info("Default profiles already exist, skipping initialization")
        }

        return profileRepository.findByNameIn(ProfileName.entries)
    }
}
