package io.github.splitfy.api.service.email

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.github.splitfy.api.domain.entity.EmailScheduleOccurrence
import io.github.splitfy.api.domain.entity.EmailScheduleSetting
import io.github.splitfy.api.repository.EmailScheduleSettingRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EmailScheduleCacheServiceTest {

    private val redisTemplate: StringRedisTemplate = mock()
    private val valueOperations: ValueOperations<String, String> = mock()
    private val repository: EmailScheduleSettingRepository = mock()
    private val properties = EmailScheduleCacheProperties().apply {
        maxAge = Duration.ofHours(24)
    }
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
    private val clock: Clock = Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC)

    private val service = EmailScheduleCacheService(
        redisTemplate = redisTemplate,
        objectMapper = objectMapper,
        emailScheduleSettingRepository = repository,
        properties = properties,
        clock = clock,
    )

    init {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)
    }

    @Test
    fun `getSchedule returns cached schedule when cache is fresh`() {
        whenever(valueOperations.get("email-schedule:DASHBOARD_EMAIL")).thenReturn(cachedScheduleJson(hoursAgo = 2))

        val schedule = service.getSchedule("DASHBOARD_EMAIL")

        assertNotNull(schedule)
        assertEquals("DASHBOARD_EMAIL", schedule.scheduleKey)
        assertEquals(1, schedule.occurrences.size)
        verify(repository, never()).findByScheduleKeyWithOccurrences(any())
    }

    @Test
    fun `getSchedule reloads from database when cache is stale`() {
        val schedule = sampleSchedule(enabled = true, time = LocalTime.of(10, 0))
        whenever(valueOperations.get("email-schedule:DASHBOARD_EMAIL")).thenReturn(cachedScheduleJson(hoursAgo = 25))
        whenever(repository.findByScheduleKeyWithOccurrences("DASHBOARD_EMAIL")).thenReturn(schedule)

        val result = service.getSchedule("DASHBOARD_EMAIL")

        assertEquals(schedule, result)
        verify(repository).findByScheduleKeyWithOccurrences("DASHBOARD_EMAIL")
        verify(valueOperations).set(eq("email-schedule:DASHBOARD_EMAIL"), any())
    }

    @Test
    fun `getSchedule reloads from database when cache is missing`() {
        val schedule = sampleSchedule(enabled = false, time = LocalTime.of(9, 30))
        whenever(valueOperations.get("email-schedule:DASHBOARD_EMAIL")).thenReturn(null)
        whenever(repository.findByScheduleKeyWithOccurrences("DASHBOARD_EMAIL")).thenReturn(schedule)

        val result = service.getSchedule("DASHBOARD_EMAIL")

        assertEquals(schedule, result)
        verify(repository).findByScheduleKeyWithOccurrences("DASHBOARD_EMAIL")
        verify(valueOperations).set(eq("email-schedule:DASHBOARD_EMAIL"), any())
    }

    @Test
    fun `getSchedule evicts cache when database no longer has the schedule`() {
        whenever(valueOperations.get("email-schedule:DASHBOARD_EMAIL")).thenReturn(cachedScheduleJson(hoursAgo = 30))
        whenever(repository.findByScheduleKeyWithOccurrences("DASHBOARD_EMAIL")).thenReturn(null)

        val result = service.getSchedule("DASHBOARD_EMAIL")

        assertEquals(null, result)
        verify(repository).findByScheduleKeyWithOccurrences("DASHBOARD_EMAIL")
        verify(redisTemplate, times(1)).delete("email-schedule:DASHBOARD_EMAIL")
    }

    private fun cachedScheduleJson(hoursAgo: Long): String {
        return objectMapper.writeValueAsString(
            mapOf(
                "id" to 1,
                "scheduleKey" to "DASHBOARD_EMAIL",
                "description" to "Dashboard",
                "enabled" to true,
                "timezone" to "America/Sao_Paulo",
                "occurrences" to listOf(
                    mapOf(
                        "id" to 1,
                        "dayOfWeek" to 2,
                        "executionTime" to "10:00:00",
                        "createdAt" to "2026-03-01T10:00:00",
                        "updatedAt" to "2026-03-01T10:00:00",
                    )
                ),
                "createdAt" to "2026-03-01T10:00:00",
                "updatedAt" to "2026-03-01T10:00:00",
                "cachedAt" to clock.instant().minus(Duration.ofHours(hoursAgo)).toString(),
            )
        )
    }

    private fun sampleSchedule(enabled: Boolean, time: LocalTime): EmailScheduleSetting {
        val setting = EmailScheduleSetting(
            id = 1L,
            scheduleKey = "DASHBOARD_EMAIL",
            description = "Dashboard",
            isEnabled = enabled,
            timezone = "America/Sao_Paulo",
            createdAt = LocalDateTime.of(2026, 3, 1, 10, 0),
            updatedAt = LocalDateTime.of(2026, 3, 1, 10, 0),
        )
        setting.occurrences = mutableListOf(
            EmailScheduleOccurrence(
                id = 1L,
                emailScheduleSetting = setting,
                dayOfWeek = 2,
                executionTime = time,
                createdAt = LocalDateTime.of(2026, 3, 1, 10, 0),
                updatedAt = LocalDateTime.of(2026, 3, 1, 10, 0),
            )
        )
        return setting
    }
}
