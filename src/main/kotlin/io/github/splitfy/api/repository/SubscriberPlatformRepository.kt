package io.github.splitfy.api.repository

import io.github.splitfy.api.domain.entity.SubscriberPlatform
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SubscriberPlatformRepository : JpaRepository<SubscriberPlatform, Long> {
    fun existsBySubscriberIdAndPlatformId(subscriberId: Long, platformId: Long): Boolean
    fun findBySubscriberIdAndPlatformId(subscriberId: Long, platformId: Long): SubscriberPlatform?
}
