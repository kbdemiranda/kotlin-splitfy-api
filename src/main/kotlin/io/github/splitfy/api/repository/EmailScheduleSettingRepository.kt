package io.github.splitfy.api.repository

import io.github.splitfy.api.domain.entity.EmailScheduleSetting
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface EmailScheduleSettingRepository : JpaRepository<EmailScheduleSetting, Long> {

    @Query(
        """
        select distinct s
        from EmailScheduleSetting s
        left join fetch s.occurrences o
        where s.scheduleKey = :scheduleKey
        """
    )
    fun findByScheduleKeyWithOccurrences(@Param("scheduleKey") scheduleKey: String): EmailScheduleSetting?
}
