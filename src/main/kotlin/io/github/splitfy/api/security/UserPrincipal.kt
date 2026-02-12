package io.github.splitfy.api.security

import io.github.splitfy.api.domain.enums.ProfileName
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.UUID

class UserPrincipal(
    private val id: UUID?,
    private val email: String,
    private val passwordHash: String,
    private val enabled: Boolean,
    private val deleted: Boolean,
    private val profileName: ProfileName?,
) : UserDetails {

    companion object {
        fun from(
            id: UUID?,
            email: String,
            passwordHash: String,
            enabled: Boolean,
            deleted: Boolean,
            profileName: ProfileName?,
        ): UserPrincipal {
            return UserPrincipal(
                id = id,
                email = email,
                passwordHash = passwordHash,
                enabled = enabled,
                deleted = deleted,
                profileName = profileName,
            )
        }
    }

    fun userId(): UUID? = id

    override fun getAuthorities(): MutableCollection<out GrantedAuthority> {
        val authority = "ROLE_${profileName?.name ?: "VIEWER"}"
        return mutableListOf(SimpleGrantedAuthority(authority))
    }

    override fun getPassword(): String = passwordHash

    override fun getUsername(): String = email

    override fun isEnabled(): Boolean = enabled && !deleted

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true
}
