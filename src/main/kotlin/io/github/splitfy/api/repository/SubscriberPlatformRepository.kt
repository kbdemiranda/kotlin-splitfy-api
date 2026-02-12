package io.github.splitfy.api.repository

import io.github.splitfy.api.domain.entity.SubscriberPlatform
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface SubscriberPlatformRepository : JpaRepository<SubscriberPlatform, Long> {
    fun existsBySubscriberIdAndPlatformId(subscriberId: Long, platformId: Long): Boolean
    fun findBySubscriberIdAndPlatformId(subscriberId: Long, platformId: Long): SubscriberPlatform?

    // Fetch active associations for a subscriber with platform entity already fetched to avoid N+1
    @Query("""
        SELECT sp FROM SubscriberPlatform sp
        JOIN FETCH sp.platform p
        WHERE sp.subscriber.id = :subscriberId
          AND sp.isActive = true
          AND sp.deletedAt IS NULL
          AND p.deletedAt IS NULL
    """)
    fun findActiveBySubscriberIdWithPlatform(@Param("subscriberId") subscriberId: Long): List<SubscriberPlatform>

    // Projection used to return counts per platform
    interface PlatformParticipantsCount {
        fun getPlatformId(): Long
        fun getCount(): Long
    }

    // Count active participants grouped by platform id for a set of platform ids
    @Query("""
        SELECT sp.platform.id AS platformId, COUNT(sp) AS count
        FROM SubscriberPlatform sp
        WHERE sp.isActive = true
          AND sp.deletedAt IS NULL
          AND sp.platform.deletedAt IS NULL
          AND sp.platform.id IN :platformIds
        GROUP BY sp.platform.id
    """)
    fun countActiveParticipantsByPlatformIds(@Param("platformIds") platformIds: List<Long>): List<PlatformParticipantsCount>
}
