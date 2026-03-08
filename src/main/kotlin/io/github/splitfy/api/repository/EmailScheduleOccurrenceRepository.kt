package io.github.splitfy.api.repository

import io.github.splitfy.api.domain.entity.EmailScheduleOccurrence
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface EmailScheduleOccurrenceRepository : JpaRepository<EmailScheduleOccurrence, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        delete from EmailScheduleOccurrence o
        where o.emailScheduleSetting.id = :emailScheduleSettingId
        """
    )
    fun deleteByEmailScheduleSettingId(@Param("emailScheduleSettingId") emailScheduleSettingId: Long): Int
}
