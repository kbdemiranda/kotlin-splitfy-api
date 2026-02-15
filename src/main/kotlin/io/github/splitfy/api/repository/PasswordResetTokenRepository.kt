package io.github.splitfy.api.repository

import io.github.splitfy.api.domain.entity.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, Long> {

    @Query(
        """
        select t
        from PasswordResetToken t
        join fetch t.user u
        where t.tokenHash = :tokenHash
          and t.usedAt is null
          and t.deletedAt is null
          and t.expiresAt > :now
          and u.deletedAt is null
        """
    )
    fun findActiveByTokenHash(
        @Param("tokenHash") tokenHash: String,
        @Param("now") now: LocalDateTime
    ): PasswordResetToken?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update PasswordResetToken t
           set t.usedAt = :now,
               t.updatedAt = :now
         where t.user.id = :userId
           and t.usedAt is null
           and t.deletedAt is null
        """
    )
    fun invalidateAllActiveByUserId(
        @Param("userId") userId: UUID,
        @Param("now") now: LocalDateTime
    ): Int
}
