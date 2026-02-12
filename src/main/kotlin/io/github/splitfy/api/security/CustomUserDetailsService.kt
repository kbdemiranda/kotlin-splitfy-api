package io.github.splitfy.api.security

import io.github.splitfy.api.repository.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository,
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(username)
            ?: throw UsernameNotFoundException("User not found for email: $username")

        return UserPrincipal(user)
    }
}
