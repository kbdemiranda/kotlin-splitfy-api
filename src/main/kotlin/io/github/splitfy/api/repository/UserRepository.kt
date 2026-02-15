package io.github.splitfy.api.repository

import io.github.splitfy.api.domain.entity.User
import io.github.splitfy.api.domain.enums.ProfileName
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    @Query(
        """
        select u.email
        from User u
        join u.profile p
        where p.name = :profileName
          and u.deletedAt is null
          and u.isEnabled = true
        """
    )
    fun findActiveEmailsByProfileName(@Param("profileName") profileName: ProfileName): List<String>
}
