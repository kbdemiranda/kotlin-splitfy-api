package io.github.splitfy.api.security

import io.github.splitfy.api.repository.UserRepository
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository,
) : UserDetailsService {

    @Transactional(readOnly = true)
    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(username)
            ?: throw UsernameNotFoundException("User not found for email: $username")

        return UserPrincipal.from(
            id = user.id,
            email = user.email,
            passwordHash = user.password,
            enabled = user.isEnabled,
            deleted = user.deletedAt != null,
            profileName = user.profile?.name,
        )
    }
}
