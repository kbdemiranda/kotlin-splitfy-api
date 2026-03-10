package io.github.splitfy.api.service.email

import io.github.splitfy.api.domain.entity.EmailScheduleOccurrence
import io.github.splitfy.api.domain.entity.EmailScheduleSetting
import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.logging.infoEvent
import io.github.splitfy.api.repository.EmailScheduleOccurrenceRepository
import io.github.splitfy.api.repository.EmailScheduleSettingRepository
import io.github.splitfy.api.web.emailschedule.dto.EmailScheduleOccurrenceResponse
import io.github.splitfy.api.web.emailschedule.dto.EmailScheduleSettingsRequest
import io.github.splitfy.api.web.emailschedule.dto.EmailScheduleSettingsResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.slf4j.LoggerFactory
import java.time.ZoneId

@Service
@Transactional
class EmailScheduleSettingsService(
    private val emailScheduleSettingRepository: EmailScheduleSettingRepository,
    private val emailScheduleOccurrenceRepository: EmailScheduleOccurrenceRepository,
    private val emailScheduleCacheService: EmailScheduleCacheService,
) {
    private val log = LoggerFactory.getLogger(EmailScheduleSettingsService::class.java)

    fun getDashboardSchedule(): EmailScheduleSettingsResponse {
        val setting = emailScheduleSettingRepository.findByScheduleKeyWithOccurrences(DASHBOARD_EMAIL_SCHEDULE_KEY)
            ?: EmailScheduleSetting(
                scheduleKey = DASHBOARD_EMAIL_SCHEDULE_KEY,
                description = DASHBOARD_EMAIL_SCHEDULE_DESCRIPTION,
                isEnabled = false,
                timezone = DEFAULT_TIMEZONE,
            )
        log.infoEvent("crud.get", "entity" to "email_schedule", "entityId" to setting.id, "scheduleKey" to setting.scheduleKey, "enabled" to setting.isEnabled)
        return toResponse(setting)
    }

    fun updateDashboardSchedule(request: EmailScheduleSettingsRequest): EmailScheduleSettingsResponse {
        validateRequest(request)

        val existingSetting = emailScheduleSettingRepository.findByScheduleKeyWithOccurrences(DASHBOARD_EMAIL_SCHEDULE_KEY)
        val setting = existingSetting
            ?: EmailScheduleSetting(
                scheduleKey = DASHBOARD_EMAIL_SCHEDULE_KEY,
                description = DASHBOARD_EMAIL_SCHEDULE_DESCRIPTION,
            )

        setting.description = DASHBOARD_EMAIL_SCHEDULE_DESCRIPTION
        setting.isEnabled = request.enabled
        setting.timezone = request.timezone.trim()
        val savedSetting = emailScheduleSettingRepository.saveAndFlush(setting)

        emailScheduleOccurrenceRepository.deleteByEmailScheduleSettingId(savedSetting.id!!)

        val newOccurrences = request.occurrences.map { occurrence ->
            EmailScheduleOccurrence(
                emailScheduleSetting = savedSetting,
                dayOfWeek = occurrence.dayOfWeek,
                executionTime = occurrence.executionTime.withSecond(0).withNano(0),
            )
        }
        if (newOccurrences.isNotEmpty()) {
            emailScheduleOccurrenceRepository.saveAllAndFlush(newOccurrences)
        }

        val reloaded = emailScheduleSettingRepository.findByScheduleKeyWithOccurrences(DASHBOARD_EMAIL_SCHEDULE_KEY)
            ?: throw IllegalStateException("Failed to reload dashboard e-mail schedule after update")
        emailScheduleCacheService.evict(DASHBOARD_EMAIL_SCHEDULE_KEY)
        emailScheduleCacheService.putSchedule(reloaded)
        log.infoEvent("crud.update", "entity" to "email_schedule", "entityId" to reloaded.id, "scheduleKey" to reloaded.scheduleKey, "enabled" to reloaded.isEnabled, "timezone" to reloaded.timezone, "occurrences" to reloaded.occurrences.size)
        return toResponse(reloaded)
    }

    private fun validateRequest(request: EmailScheduleSettingsRequest) {
        val timezone = request.timezone.trim()
        if (timezone.isBlank()) {
            throw BadRequestApiException("timezone must not be blank")
        }
        runCatching { ZoneId.of(timezone) }
            .getOrElse { throw BadRequestApiException("Invalid timezone: $timezone") }

        val duplicates = request.occurrences
            .groupBy { it.dayOfWeek to it.executionTime.withSecond(0).withNano(0) }
            .filterValues { it.size > 1 }
            .keys

        if (duplicates.isNotEmpty()) {
            throw BadRequestApiException("Schedule occurrences must be unique by dayOfWeek and executionTime")
        }
    }

    private fun toResponse(setting: EmailScheduleSetting): EmailScheduleSettingsResponse {
        return EmailScheduleSettingsResponse(
            scheduleKey = setting.scheduleKey,
            enabled = setting.isEnabled,
            timezone = setting.timezone,
            occurrences = setting.occurrences
                .sortedWith(compareBy<EmailScheduleOccurrence> { it.dayOfWeek }.thenBy { it.executionTime })
                .map {
                    EmailScheduleOccurrenceResponse(
                        dayOfWeek = it.dayOfWeek,
                        executionTime = it.executionTime.withSecond(0).withNano(0),
                    )
                }
        )
    }

    companion object {
        private const val DASHBOARD_EMAIL_SCHEDULE_KEY = "DASHBOARD_EMAIL"
        private const val DASHBOARD_EMAIL_SCHEDULE_DESCRIPTION = "Scheduled dashboard e-mail dispatch"
        private const val DEFAULT_TIMEZONE = "America/Sao_Paulo"
    }
}
