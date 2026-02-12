package io.github.splitfy.api.repository

import io.github.splitfy.api.domain.entity.Profile
import io.github.splitfy.api.domain.enums.ProfileName
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ProfileRepository : JpaRepository<Profile, UUID> {
    fun findByName(name: ProfileName): Profile?

    fun existsByName(name: ProfileName): Boolean

    fun findByNameIn(names: Collection<ProfileName>): List<Profile>
}
