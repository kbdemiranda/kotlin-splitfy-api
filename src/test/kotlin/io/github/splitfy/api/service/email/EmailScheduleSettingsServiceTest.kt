package io.github.splitfy.api.service.email

import io.github.splitfy.api.domain.entity.EmailScheduleOccurrence
import io.github.splitfy.api.domain.entity.EmailScheduleSetting
import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.repository.EmailScheduleOccurrenceRepository
import io.github.splitfy.api.repository.EmailScheduleSettingRepository
import io.github.splitfy.api.web.emailschedule.dto.EmailScheduleOccurrenceRequest
import io.github.splitfy.api.web.emailschedule.dto.EmailScheduleSettingsRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EmailScheduleSettingsServiceTest {

    private val repository: EmailScheduleSettingRepository = mock()
    private val occurrenceRepository: EmailScheduleOccurrenceRepository = mock()
    private val cacheService: EmailScheduleCacheService = mock()

    private val service = EmailScheduleSettingsService(repository, occurrenceRepository, cacheService)

    @Test
    fun `getKpiSummarySchedule returns default disabled config when setting is missing`() {
        whenever(repository.findByScheduleKeyWithOccurrences("KPI_SUMMARY_EMAIL")).thenReturn(null)
        whenever(repository.findByScheduleKeyWithOccurrences("DASHBOARD_EMAIL")).thenReturn(null)

        val response = service.getKpiSummarySchedule()

        assertEquals("KPI_SUMMARY_EMAIL", response.scheduleKey)
        assertFalse(response.enabled)
        assertEquals("America/Sao_Paulo", response.timezone)
        assertEquals(0, response.occurrences.size)
    }

    @Test
    fun `updateKpiSummarySchedule replaces occurrences and sorts response`() {
        val existing = EmailScheduleSetting(
            id = 1L,
            scheduleKey = "KPI_SUMMARY_EMAIL",
            description = "Dashboard",
            isEnabled = true,
            timezone = "America/Sao_Paulo",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        whenever(repository.findByScheduleKeyWithOccurrences("KPI_SUMMARY_EMAIL")).thenReturn(existing)
        whenever(repository.saveAndFlush(any())).thenAnswer { it.getArgument(0) }
        whenever(repository.findByScheduleKeyWithOccurrences("KPI_SUMMARY_EMAIL")).thenReturn(existing)
            .thenReturn(
                existing.copy(
                    occurrences = mutableListOf(
                        EmailScheduleOccurrence(
                            id = 2L,
                            emailScheduleSetting = existing,
                            dayOfWeek = 1,
                            executionTime = LocalTime.of(10, 0),
                            createdAt = LocalDateTime.now(),
                            updatedAt = LocalDateTime.now(),
                        ),
                        EmailScheduleOccurrence(
                            id = 3L,
                            emailScheduleSetting = existing,
                            dayOfWeek = 5,
                            executionTime = LocalTime.of(12, 0),
                            createdAt = LocalDateTime.now(),
                            updatedAt = LocalDateTime.now(),
                        )
                    )
                )
            )

        val response = service.updateKpiSummarySchedule(
            EmailScheduleSettingsRequest(
                enabled = true,
                timezone = "America/Sao_Paulo",
                occurrences = listOf(
                    EmailScheduleOccurrenceRequest(dayOfWeek = 5, executionTime = LocalTime.of(12, 0)),
                    EmailScheduleOccurrenceRequest(dayOfWeek = 1, executionTime = LocalTime.of(10, 0))
                )
            )
        )

        assertEquals(2, response.occurrences.size)
        assertEquals(1, response.occurrences[0].dayOfWeek)
        assertEquals(LocalTime.of(10, 0), response.occurrences[0].executionTime)
        assertEquals(5, response.occurrences[1].dayOfWeek)
        assertEquals(LocalTime.of(12, 0), response.occurrences[1].executionTime)
        verify(occurrenceRepository).deleteByEmailScheduleSettingId(1L)
        verify(repository).saveAndFlush(any())
        inOrder(cacheService) {
            verify(cacheService).evict("KPI_SUMMARY_EMAIL")
            verify(cacheService).putSchedule(any())
        }
        verify(occurrenceRepository).saveAllAndFlush(
            argThat<MutableIterable<EmailScheduleOccurrence>> {
                this.count() == 2
            }
        )
    }

    @Test
    fun `updateKpiSummarySchedule migrates legacy dashboard key`() {
        val existing = EmailScheduleSetting(
            id = 1L,
            scheduleKey = "DASHBOARD_EMAIL",
            description = "Dashboard",
            isEnabled = true,
            timezone = "America/Sao_Paulo",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        whenever(repository.findByScheduleKeyWithOccurrences("KPI_SUMMARY_EMAIL")).thenReturn(
            null,
            existing.copy(scheduleKey = "KPI_SUMMARY_EMAIL")
        )
        whenever(repository.findByScheduleKeyWithOccurrences("DASHBOARD_EMAIL")).thenReturn(existing)
        whenever(repository.saveAndFlush(any())).thenAnswer { it.getArgument(0) }

        val response = service.updateKpiSummarySchedule(
            EmailScheduleSettingsRequest(
                enabled = true,
                timezone = "America/Sao_Paulo",
                occurrences = listOf(
                    EmailScheduleOccurrenceRequest(dayOfWeek = 1, executionTime = LocalTime.of(10, 0))
                )
            )
        )

        assertEquals("KPI_SUMMARY_EMAIL", response.scheduleKey)
        verify(cacheService).evict("DASHBOARD_EMAIL")
        verify(cacheService).evict("KPI_SUMMARY_EMAIL")
    }

    @Test
    fun `updateKpiSummarySchedule rejects duplicate occurrences`() {
        assertThrows<BadRequestApiException> {
            service.updateKpiSummarySchedule(
                EmailScheduleSettingsRequest(
                    enabled = true,
                    timezone = "America/Sao_Paulo",
                    occurrences = listOf(
                        EmailScheduleOccurrenceRequest(dayOfWeek = 1, executionTime = LocalTime.of(10, 0)),
                        EmailScheduleOccurrenceRequest(dayOfWeek = 1, executionTime = LocalTime.of(10, 0))
                    )
                )
            )
        }
    }

    @Test
    fun `updateKpiSummarySchedule rejects invalid timezone`() {
        assertThrows<BadRequestApiException> {
            service.updateKpiSummarySchedule(
                EmailScheduleSettingsRequest(
                    enabled = true,
                    timezone = "invalid/timezone",
                    occurrences = listOf(
                        EmailScheduleOccurrenceRequest(dayOfWeek = 1, executionTime = LocalTime.of(10, 0))
                    )
                )
            )
        }
    }
}
