package io.github.splitfy.api.domain.repository

import io.github.splitfy.api.domain.models.Platform
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PlatformRepository : JpaRepository<Platform, Long>

