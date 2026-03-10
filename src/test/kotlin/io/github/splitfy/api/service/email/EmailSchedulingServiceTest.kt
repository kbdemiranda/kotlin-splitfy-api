package io.github.splitfy.api.service.email

import io.github.splitfy.api.domain.entity.EmailScheduleOccurrence
import io.github.splitfy.api.domain.entity.EmailScheduleSetting
import io.github.splitfy.api.domain.entity.User
import io.github.splitfy.api.repository.UserRepository
import io.github.splitfy.api.service.dashboard.DashboardService
import io.github.splitfy.api.web.dashboard.dto.DashboardKpiResponse
import io.github.splitfy.api.web.dashboard.dto.DebtorSubscriberItem
import io.github.splitfy.api.web.dashboard.dto.PendingByPlatformItem
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

class EmailSchedulingServiceTest {

    private val emailService: EmailService = mock()
    private val dashboardService: DashboardService = mock()
    private val userRepository: UserRepository = mock()
    private val emailScheduleCacheService: EmailScheduleCacheService = mock()
    private val templateService = EmailTemplateService()

    @Test
    fun `sendDailyKpiSummaryEmail sends KPI summary when schedule matches and recipient is configured`() {
        whenever(dashboardService.getKpis(null)).thenReturn(sampleKpis())
        whenever(emailScheduleCacheService.getSchedule("KPI_SUMMARY_EMAIL")).thenReturn(
            schedule(dayOfWeek = 1, time = LocalTime.of(10, 0), timezone = "America/Sao_Paulo", enabled = true)
        )
        whenever(userRepository.findByReceivesDashboardEmailTrueAndDeletedAtIsNullAndIsEnabledTrue()).thenReturn(
            activeRecipient("finance@splitfy.com")
        )

        val service = EmailSchedulingService(
            emailService = emailService,
            emailTemplateService = templateService,
            dashboardService = dashboardService,
            userRepository = userRepository,
            emailScheduleCacheService = emailScheduleCacheService,
            clock = Clock.fixed(Instant.parse("2026-03-09T13:00:00Z"), ZoneOffset.UTC)
        )

        service.sendDailyKpiSummaryEmail()

        verify(emailService).sendHtml(
            eq("finance@splitfy.com"),
            eq("Resumo de KPIs Splitfy - março de 2026"),
            any(),
            any()
        )
    }

    @Test
    fun `sendDailyKpiSummaryEmail does nothing when schedule is disabled`() {
        whenever(emailScheduleCacheService.getSchedule("KPI_SUMMARY_EMAIL")).thenReturn(
            schedule(dayOfWeek = 1, time = LocalTime.of(10, 0), timezone = "America/Sao_Paulo", enabled = false)
        )

        val service = EmailSchedulingService(
            emailService = emailService,
            emailTemplateService = templateService,
            dashboardService = dashboardService,
            userRepository = userRepository,
            emailScheduleCacheService = emailScheduleCacheService,
            clock = Clock.fixed(Instant.parse("2026-03-09T13:00:00Z"), ZoneOffset.UTC)
        )

        service.sendDailyKpiSummaryEmail()

        verify(userRepository, never()).findByReceivesDashboardEmailTrueAndDeletedAtIsNullAndIsEnabledTrue()
        verify(dashboardService, never()).getKpis(any())
        verify(emailService, never()).sendHtml(any(), any(), any(), any())
    }

    @Test
    fun `sendDailyKpiSummaryEmail does nothing when current time does not match configured slots`() {
        whenever(emailScheduleCacheService.getSchedule("KPI_SUMMARY_EMAIL")).thenReturn(
            schedule(dayOfWeek = 1, time = LocalTime.of(11, 0), timezone = "America/Sao_Paulo", enabled = true)
        )

        val service = EmailSchedulingService(
            emailService = emailService,
            emailTemplateService = templateService,
            dashboardService = dashboardService,
            userRepository = userRepository,
            emailScheduleCacheService = emailScheduleCacheService,
            clock = Clock.fixed(Instant.parse("2026-03-09T13:00:00Z"), ZoneOffset.UTC)
        )

        service.sendDailyKpiSummaryEmail()

        verify(userRepository, never()).findByReceivesDashboardEmailTrueAndDeletedAtIsNullAndIsEnabledTrue()
        verify(dashboardService, never()).getKpis(any())
        verify(emailService, never()).sendHtml(any(), any(), any(), any())
    }

    @Test
    fun `sendDailyKpiSummaryEmail does nothing when no active recipient is configured`() {
        whenever(emailScheduleCacheService.getSchedule("KPI_SUMMARY_EMAIL")).thenReturn(
            schedule(dayOfWeek = 1, time = LocalTime.of(10, 0), timezone = "America/Sao_Paulo", enabled = true)
        )

        val service = EmailSchedulingService(
            emailService = emailService,
            emailTemplateService = templateService,
            dashboardService = dashboardService,
            userRepository = userRepository,
            emailScheduleCacheService = emailScheduleCacheService,
            clock = Clock.fixed(Instant.parse("2026-03-09T13:00:00Z"), ZoneOffset.UTC)
        )

        service.sendDailyKpiSummaryEmail()

        verify(userRepository).findByReceivesDashboardEmailTrueAndDeletedAtIsNullAndIsEnabledTrue()
        verify(dashboardService, never()).getKpis(any())
        verify(emailService, never()).sendHtml(any(), any(), any(), any())
    }

    private fun schedule(
        dayOfWeek: Int,
        time: LocalTime,
        timezone: String,
        enabled: Boolean,
    ): EmailScheduleSetting {
        val setting = EmailScheduleSetting(
            id = 1L,
            scheduleKey = "KPI_SUMMARY_EMAIL",
            description = "Dashboard",
            isEnabled = enabled,
            timezone = timezone,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        setting.occurrences = mutableListOf(
            EmailScheduleOccurrence(
                id = 1L,
                emailScheduleSetting = setting,
                dayOfWeek = dayOfWeek,
                executionTime = time,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )
        )
        return setting
    }

    private fun sampleKpis(): DashboardKpiResponse {
        return DashboardKpiResponse(
            referenceMonth = YearMonth.of(2026, 3),
            currency = "BRL",
            totalDue = BigDecimal("138.67"),
            totalPaid = BigDecimal("126.26"),
            totalPending = BigDecimal("12.41"),
            totalUnpaid = BigDecimal("0.00"),
            delinquencyRate = BigDecimal("8.95"),
            pendingByPlatform = listOf(
                PendingByPlatformItem(
                    platformId = 10L,
                    platformName = "Netflix",
                    pendingCount = 1,
                    pendingAmount = BigDecimal("12.41")
                )
            ),
            debtors = listOf(
                DebtorSubscriberItem(
                    subscriberId = 1L,
                    subscriberName = "Eduardo Henrique",
                    subscriberEmail = "eduardoehp@outlook.com",
                    pendingAmount = BigDecimal("12.41"),
                    unpaidAmount = BigDecimal("0.00"),
                    totalDebt = BigDecimal("12.41")
                )
            )
        )
    }

    private fun activeRecipient(email: String): User {
        return User(
            id = UUID.randomUUID(),
            name = "Recipient",
            email = email,
            password = "secret",
            isEnabled = true,
            receivesDashboardEmail = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
    }
}
