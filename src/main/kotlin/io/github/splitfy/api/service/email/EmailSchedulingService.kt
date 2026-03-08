package io.github.splitfy.api.service.email

import io.github.splitfy.api.service.dashboard.DashboardService
import io.github.splitfy.api.web.dashboard.dto.DashboardKpiResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Service
class EmailSchedulingService(
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    private val dashboardService: DashboardService,
    @Value("\${splitfy.mail.schedules.dashboard.enabled:false}") private val dashboardEmailEnabled: Boolean,
    @Value("\${splitfy.mail.schedules.dashboard.recipient:}") private val dashboardEmailRecipient: String,
) {

    private val log = LoggerFactory.getLogger(EmailSchedulingService::class.java)
    private val finalScale = 2
    private val rounding = RoundingMode.HALF_UP

    @Scheduled(
        cron = "\${splitfy.mail.schedules.dashboard.cron:0 0 9 * * *}",
        zone = "\${splitfy.mail.schedules.dashboard.zone:America/Sao_Paulo}"
    )
    fun sendDailyDashboardEmail() {
        if (!dashboardEmailEnabled) {
            log.debug("Dashboard scheduled e-mail dispatch is disabled")
            return
        }

        val recipient = dashboardEmailRecipient.trim()
        if (recipient.isBlank()) {
            log.warn("Dashboard scheduled e-mail dispatch skipped because no recipient was configured")
            return
        }

        val kpis = dashboardService.getKpis(null)
        val subject = "Resumo do dashboard Splitfy - ${formatReferenceMonth(kpis.referenceMonth)}"
        val htmlBody = buildDashboardEmailHtml(kpis)

        emailService.sendHtml(
            to = recipient,
            subject = subject,
            htmlBody = htmlBody
        )

        log.info("Dashboard scheduled e-mail sent to {}", recipient)
    }

    internal fun buildDashboardEmailHtml(kpis: DashboardKpiResponse): String {
        val totalDue = kpis.totalDue.setScale(finalScale, rounding)
        val statusItems = listOf(
            StatusSummaryItem(
                label = "Pago",
                amount = formatCurrency(kpis.totalPaid),
                percentage = calculatePercentage(kpis.totalPaid, totalDue),
                accentColor = "#16a34a"
            ),
            StatusSummaryItem(
                label = "Pendente",
                amount = formatCurrency(kpis.totalPending),
                percentage = calculatePercentage(kpis.totalPending, totalDue),
                accentColor = "#f59e0b"
            ),
            StatusSummaryItem(
                label = "Nao pago",
                amount = formatCurrency(kpis.totalUnpaid),
                percentage = calculatePercentage(kpis.totalUnpaid, totalDue),
                accentColor = "#ef4444"
            )
        )

        return emailTemplateService.renderTemplate(
            templateName = "email/dashboard-kpis",
            variables = mapOf(
                "preheader" to "Resumo do dashboard Splitfy - ${formatReferenceMonth(kpis.referenceMonth)}",
                "referenceMonthLabel" to formatReferenceMonth(kpis.referenceMonth),
                "totalDue" to formatCurrency(kpis.totalDue),
                "totalPaid" to formatCurrency(kpis.totalPaid),
                "totalPending" to formatCurrency(kpis.totalPending),
                "totalUnpaid" to formatCurrency(kpis.totalUnpaid),
                "delinquencyRate" to formatPercentage(kpis.delinquencyRate),
                "statusItems" to statusItems,
                "pendingByPlatform" to kpis.pendingByPlatform.map {
                    PendingPlatformRow(
                        platformName = it.platformName,
                        pendingCount = it.pendingCount,
                        pendingAmount = formatCurrency(it.pendingAmount)
                    )
                },
                "debtors" to kpis.debtors.map {
                    DebtorRow(
                        subscriberName = it.subscriberName,
                        subscriberEmail = it.subscriberEmail,
                        pendingAmount = formatCurrency(it.pendingAmount),
                        unpaidAmount = formatCurrency(it.unpaidAmount),
                        totalDebt = formatCurrency(it.totalDebt)
                    )
                }
            )
        )
    }

    private fun calculatePercentage(amount: BigDecimal, total: BigDecimal): String {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return formatPercentage(BigDecimal.ZERO)
        }

        return formatPercentage(
            amount.setScale(finalScale, rounding)
                .divide(total, 10, rounding)
                .multiply(BigDecimal("100"))
        )
    }

    private fun formatCurrency(value: BigDecimal): String {
        return "R$ ${value.setScale(finalScale, rounding).toPlainString()}"
    }

    private fun formatPercentage(value: BigDecimal): String {
        return "${value.setScale(finalScale, rounding).toPlainString()}%"
    }

    private fun formatReferenceMonth(referenceMonth: YearMonth): String {
        return referenceMonth.format(REFERENCE_MONTH_FORMATTER)
    }

    private data class StatusSummaryItem(
        val label: String,
        val amount: String,
        val percentage: String,
        val accentColor: String,
    )

    private data class PendingPlatformRow(
        val platformName: String,
        val pendingCount: Int,
        val pendingAmount: String,
    )

    private data class DebtorRow(
        val subscriberName: String,
        val subscriberEmail: String,
        val pendingAmount: String,
        val unpaidAmount: String,
        val totalDebt: String,
    )

    companion object {
        private val REFERENCE_MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale("pt", "BR"))
    }
}
