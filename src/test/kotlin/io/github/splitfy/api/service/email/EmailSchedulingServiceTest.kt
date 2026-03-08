package io.github.splitfy.api.service.email

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
import java.time.YearMonth

class EmailSchedulingServiceTest {

    private val emailService: EmailService = mock()
    private val dashboardService: DashboardService = mock()
    private val templateService = EmailTemplateService()

    @Test
    fun `sendDailyDashboardEmail sends dashboard summary when enabled and recipient is configured`() {
        whenever(dashboardService.getKpis(null)).thenReturn(sampleKpis())

        val service = EmailSchedulingService(
            emailService = emailService,
            emailTemplateService = templateService,
            dashboardService = dashboardService,
            dashboardEmailEnabled = true,
            dashboardEmailRecipient = "finance@splitfy.com"
        )

        service.sendDailyDashboardEmail()

        verify(emailService).sendHtml(
            eq("finance@splitfy.com"),
            eq("Resumo do dashboard Splitfy - março de 2026"),
            any(),
            any()
        )
    }

    @Test
    fun `sendDailyDashboardEmail does nothing when disabled`() {
        val service = EmailSchedulingService(
            emailService = emailService,
            emailTemplateService = templateService,
            dashboardService = dashboardService,
            dashboardEmailEnabled = false,
            dashboardEmailRecipient = "finance@splitfy.com"
        )

        service.sendDailyDashboardEmail()

        verify(dashboardService, never()).getKpis(any())
        verify(emailService, never()).sendHtml(any(), any(), any(), any())
    }

    @Test
    fun `sendDailyDashboardEmail does nothing when recipient is blank`() {
        val service = EmailSchedulingService(
            emailService = emailService,
            emailTemplateService = templateService,
            dashboardService = dashboardService,
            dashboardEmailEnabled = true,
            dashboardEmailRecipient = "   "
        )

        service.sendDailyDashboardEmail()

        verify(dashboardService, never()).getKpis(any())
        verify(emailService, never()).sendHtml(any(), any(), any(), any())
    }

    private fun sampleKpis(): DashboardKpiResponse {
        return DashboardKpiResponse(
            referenceMonth = YearMonth.of(2026, 3),
            currency = "BRL",
            totalDue = BigDecimal("138.67"),
            totalPaid = BigDecimal("126.26"),
            totalPending = BigDecimal("0.00"),
            totalUnpaid = BigDecimal("12.41"),
            delinquencyRate = BigDecimal("8.95"),
            pendingByPlatform = listOf(
                PendingByPlatformItem(
                    platformId = 10L,
                    platformName = "Netflix",
                    pendingCount = 1,
                    pendingAmount = BigDecimal("0.00")
                )
            ),
            debtors = listOf(
                DebtorSubscriberItem(
                    subscriberId = 1L,
                    subscriberName = "Eduardo Henrique",
                    subscriberEmail = "eduardoehp@outlook.com",
                    pendingAmount = BigDecimal("0.00"),
                    unpaidAmount = BigDecimal("12.41"),
                    totalDebt = BigDecimal("12.41")
                )
            )
        )
    }
}
