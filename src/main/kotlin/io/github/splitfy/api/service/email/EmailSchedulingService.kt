package io.github.splitfy.api.service.email

import io.github.splitfy.api.domain.entity.EmailScheduleSetting
import io.github.splitfy.api.repository.UserRepository
import io.github.splitfy.api.service.dashboard.DashboardService
import io.github.splitfy.api.web.dashboard.dto.DashboardKpiResponse
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Service
class EmailSchedulingService(
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    private val dashboardService: DashboardService,
    private val userRepository: UserRepository,
    private val emailScheduleCacheService: EmailScheduleCacheService,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val log = LoggerFactory.getLogger(EmailSchedulingService::class.java)
    private val finalScale = 2
    private val rounding = RoundingMode.HALF_UP

    @Scheduled(cron = "0 * * * * *")
    fun sendDailyDashboardEmail() {
        val schedule = emailScheduleCacheService.getSchedule(DASHBOARD_EMAIL_SCHEDULE_KEY)
        if (schedule == null) {
            log.debug("Dashboard scheduled e-mail dispatch skipped because schedule {} is not configured", DASHBOARD_EMAIL_SCHEDULE_KEY)
            return
        }
        if (!schedule.isEnabled) {
            log.debug("Dashboard scheduled e-mail dispatch is disabled in database")
            return
        }
        if (!shouldRunNow(schedule)) {
            return
        }

        val recipient = userRepository.findByReceivesDashboardEmailTrueAndDeletedAtIsNullAndIsEnabledTrue()?.email?.trim()
        if (recipient.isNullOrBlank()) {
            log.warn("Dashboard scheduled e-mail dispatch skipped because no active user is configured as recipient")
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

        log.info(
            "event=email.dashboard.scheduled.sent entity=email_schedule entityId={} scheduleKey={} recipient={} referenceMonth={}",
            schedule.id,
            schedule.scheduleKey,
            recipient,
            kpis.referenceMonth,
        )
    }

    internal fun shouldRunNow(schedule: EmailScheduleSetting, now: ZonedDateTime = nowInScheduleZone(schedule.timezone)): Boolean {
        if (!schedule.isEnabled) {
            return false
        }

        val currentDayOfWeek = now.dayOfWeek.toDatabaseValue()
        val currentTime = now.toLocalTime().withSecond(0).withNano(0)
        return schedule.occurrences.any { occurrence ->
            occurrence.dayOfWeek == currentDayOfWeek &&
                occurrence.executionTime.withSecond(0).withNano(0) == currentTime
        }
    }

    private fun nowInScheduleZone(timezone: String): ZonedDateTime {
        return ZonedDateTime.now(clock).withZoneSameInstant(ZoneId.of(timezone))
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
            )
        )

        return emailTemplateService.renderTemplate(
            templateName = "email/dashboard-kpis",
            variables = mapOf(
                "preheader" to "Resumo do dashboard Splitfy - ${formatReferenceMonth(kpis.referenceMonth)}",
                "referenceMonthLabel" to formatReferenceMonth(kpis.referenceMonth),
                "totalPendingHighlight" to (kpis.totalPending > BigDecimal.ZERO),
                "totalPaid" to formatCurrency(kpis.totalPaid),
                "totalPending" to formatCurrency(kpis.totalPending),
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
        val totalDebt: String,
    )

    companion object {
        private const val DASHBOARD_EMAIL_SCHEDULE_KEY = "DASHBOARD_EMAIL"
        private val REFERENCE_MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale("pt", "BR"))
    }
}

private fun DayOfWeek.toDatabaseValue(): Int = value
