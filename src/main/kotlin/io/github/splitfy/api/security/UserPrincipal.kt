package io.github.splitfy.api.security

import io.github.splitfy.api.domain.entity.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.UUID

class UserPrincipal(private val user: User) : UserDetails {

    fun userId(): UUID? = user.id

    override fun getAuthorities(): MutableCollection<out GrantedAuthority> {
        val authority = "ROLE_${user.profile?.name?.name ?: "VIEWER"}"
        return mutableListOf(SimpleGrantedAuthority(authority))
    }

    override fun getPassword(): String = user.password

    override fun getUsername(): String = user.email

    override fun isEnabled(): Boolean = user.isEnabled && user.deletedAt == null

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true
}
