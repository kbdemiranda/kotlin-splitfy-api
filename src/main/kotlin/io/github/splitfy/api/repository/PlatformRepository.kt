package io.github.splitfy.api.repository

import io.github.splitfy.api.domain.entity.Platform
import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.domain.enums.ServiceType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PlatformRepository : JpaRepository<Platform, Long> {

    fun findByDeletedAtIsNull(pageable: Pageable): Page<Platform>

    fun findByDeletedAtIsNullAndNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Platform>

}

