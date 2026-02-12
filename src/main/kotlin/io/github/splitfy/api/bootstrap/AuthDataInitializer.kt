package io.github.splitfy.api.bootstrap

import io.github.splitfy.api.domain.entity.Profile
import io.github.splitfy.api.domain.entity.User
import io.github.splitfy.api.domain.enums.ProfileName
import io.github.splitfy.api.repository.ProfileRepository
import io.github.splitfy.api.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class AuthDataInitializer(
    private val profileRepository: ProfileRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${splitfy.admin.email:admin@splitfy.local}") private val adminEmail: String?,
    @Value("\${splitfy.admin.password:Admin@123}") private val adminPassword: String?,
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(AuthDataInitializer::class.java)

    override fun run(args: ApplicationArguments) {
        val profiles = ensureProfiles()
        ensureAdminUser(profiles)
    }

    private fun ensureProfiles(): List<Profile> {
        val existingProfiles = profileRepository.findByNameIn(ProfileName.entries)
        val existingNames = existingProfiles.map { it.name }.toSet()

        val missingProfiles = ProfileName.entries
            .filterNot { existingNames.contains(it) }
            .map { Profile(name = it) }

        if (missingProfiles.isNotEmpty()) {
            profileRepository.saveAll(missingProfiles)
            logger.info("Created missing profiles: {}", missingProfiles.map { it.name })
        }

        return profileRepository.findByNameIn(ProfileName.entries)
    }

    private fun ensureAdminUser(profiles: List<Profile>) {
        val normalizedEmail = adminEmail?.lowercase()?.trim()
            ?: throw IllegalStateException("Admin email must not be null")
        val rawPassword = adminPassword
            ?: throw IllegalStateException("Admin password must not be null")

        val existingAdmin = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail)
        if (existingAdmin != null) {
            return
        }

        val adminProfile = profiles.firstOrNull { it.name == ProfileName.ADMIN }
            ?: throw IllegalStateException("ADMIN profile not found after initialization")

        val adminUser = User(
            name = "System Admin",
            email = normalizedEmail,
            password = passwordEncoder.encode(rawPassword)
                ?: throw IllegalStateException("Password encoding failed"),
            profile = adminProfile,
            isEnabled = true,
        )

        userRepository.save(adminUser)
        logger.info("Default admin user created with email {}", normalizedEmail)
    }
}
