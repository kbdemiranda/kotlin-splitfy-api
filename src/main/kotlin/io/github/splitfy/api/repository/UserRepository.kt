package io.github.splitfy.api.repository

import io.github.splitfy.api.domain.entity.User
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    fun findByIdAndDeletedAtIsNull(id: UUID): User?

    fun findByDeletedAtIsNull(pageable: Pageable): Page<User>

    fun findByDeletedAtIsNullAndNameContainingIgnoreCase(name: String, pageable: Pageable): Page<User>

    @EntityGraph(attributePaths = ["profile"])
    fun findByEmailIgnoreCaseAndDeletedAtIsNull(email: String): User?

    fun existsByEmailIgnoreCaseAndDeletedAtIsNull(email: String): Boolean

    fun existsByEmailIgnoreCaseAndDeletedAtIsNullAndIdNot(email: String, id: UUID): Boolean

    fun existsByProfileIdAndDeletedAtIsNull(profileId: UUID): Boolean
}
