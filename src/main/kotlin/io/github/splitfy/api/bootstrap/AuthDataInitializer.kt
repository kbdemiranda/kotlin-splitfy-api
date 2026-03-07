package io.github.splitfy.api.bootstrap

import io.github.splitfy.api.domain.entity.Profile
import io.github.splitfy.api.domain.entity.User
import io.github.splitfy.api.domain.enums.ProfileName
import io.github.splitfy.api.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class AuthDataInitializer(
    private val defaultProfileBootstrap: DefaultProfileBootstrap,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${splitfy.admin.email}") private val adminEmail: String?,
    @Value("\${splitfy.admin.password}") private val adminPassword: String?,
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(AuthDataInitializer::class.java)

    override fun run(args: ApplicationArguments) {
        val profiles = defaultProfileBootstrap.ensureProfiles()
        ensureAdminUser(profiles)
    }

    private fun ensureAdminUser(profiles: List<Profile>) {
        val normalizedEmail = adminEmail?.lowercase()?.trim().orEmpty()
        val rawPassword = adminPassword?.trim().orEmpty()
        if (normalizedEmail.isBlank() || rawPassword.isBlank()) {
            logger.warn("Admin bootstrap skipped: splitfy.admin.email/password not configured")
            return
        }

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
