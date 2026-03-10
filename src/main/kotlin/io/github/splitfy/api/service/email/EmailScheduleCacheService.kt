package io.github.splitfy.api.service.email

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.splitfy.api.domain.entity.EmailScheduleOccurrence
import io.github.splitfy.api.domain.entity.EmailScheduleSetting
import io.github.splitfy.api.repository.EmailScheduleSettingRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class EmailScheduleCacheService(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val emailScheduleSettingRepository: EmailScheduleSettingRepository,
    private val properties: EmailScheduleCacheProperties,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val log = LoggerFactory.getLogger(EmailScheduleCacheService::class.java)

    fun getSchedule(scheduleKey: String): EmailScheduleSetting? {
        val cached = getCachedPayload(scheduleKey)
        if (cached != null && !cached.isStale(clock.instant(), properties.maxAge)) {
            return cached.toEntity()
        }

        val fresh = emailScheduleSettingRepository.findByScheduleKeyWithOccurrences(scheduleKey)
        if (fresh == null) {
            evict(scheduleKey)
            return null
        }

        putSchedule(fresh)
        return fresh
    }

    fun putSchedule(schedule: EmailScheduleSetting) {
        runCatching {
            val cacheKey = cacheKey(schedule.scheduleKey)
            val payload = CachedEmailSchedule.from(schedule, clock.instant())
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(payload))
        }.onFailure { ex ->
            log.warn("Failed to cache email schedule {}", schedule.scheduleKey, ex)
        }
    }

    fun evict(scheduleKey: String) {
        runCatching {
            redisTemplate.delete(cacheKey(scheduleKey))
        }.onFailure { ex ->
            log.warn("Failed to evict cached email schedule {}", scheduleKey, ex)
        }
    }

    private fun getCachedPayload(scheduleKey: String): CachedEmailSchedule? {
        return runCatching {
            val rawValue = redisTemplate.opsForValue().get(cacheKey(scheduleKey)) ?: return null
            objectMapper.readValue(rawValue, CachedEmailSchedule::class.java)
        }.onFailure { ex ->
            log.warn("Failed to read cached email schedule {} from Redis", scheduleKey, ex)
            evict(scheduleKey)
        }.getOrNull()
    }

    private fun cacheKey(scheduleKey: String): String = "$CACHE_KEY_PREFIX:$scheduleKey"

    private class CachedEmailSchedule {
        var id: Long? = null
        var scheduleKey: String = ""
        var description: String? = null
        var isEnabled: Boolean = false
        var timezone: String = "America/Sao_Paulo"
        var occurrences: List<CachedEmailScheduleOccurrence> = emptyList()
        var createdAt: LocalDateTime? = null
        var updatedAt: LocalDateTime? = null
        var cachedAt: Instant = Instant.EPOCH

        fun isStale(now: Instant, maxAge: java.time.Duration): Boolean = cachedAt.plus(maxAge).isBefore(now)

        fun toEntity(): EmailScheduleSetting {
            val setting = EmailScheduleSetting(
                id = id,
                scheduleKey = scheduleKey,
                description = description,
                isEnabled = isEnabled,
                timezone = timezone,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
            setting.occurrences = occurrences.map {
                EmailScheduleOccurrence(
                    id = it.id,
                    emailScheduleSetting = setting,
                    dayOfWeek = it.dayOfWeek,
                    executionTime = it.executionTime,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                )
            }.toMutableList()
            return setting
        }

        companion object {
            fun from(schedule: EmailScheduleSetting, cachedAt: Instant): CachedEmailSchedule {
                return CachedEmailSchedule().apply {
                    id = schedule.id
                    scheduleKey = schedule.scheduleKey
                    description = schedule.description
                    isEnabled = schedule.isEnabled
                    timezone = schedule.timezone
                    occurrences = schedule.occurrences.map {
                        CachedEmailScheduleOccurrence().apply {
                            id = it.id
                            dayOfWeek = it.dayOfWeek
                            executionTime = it.executionTime
                            createdAt = it.createdAt
                            updatedAt = it.updatedAt
                        }
                    }
                    createdAt = schedule.createdAt
                    updatedAt = schedule.updatedAt
                    this.cachedAt = cachedAt
                }
            }
        }
    }

    private class CachedEmailScheduleOccurrence {
        var id: Long? = null
        var dayOfWeek: Int = 0
        lateinit var executionTime: LocalTime
        var createdAt: LocalDateTime? = null
        var updatedAt: LocalDateTime? = null
    }

    companion object {
        private const val CACHE_KEY_PREFIX = "email-schedule"
    }
}
