package io.github.splitfy.api.web.dashboard.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.YearMonth

@Schema(description = "Billing dashboard KPI response")
data class DashboardKpiResponse(
    @Schema(description = "Reference month for the KPI calculation", example = "2026-02")
    val referenceMonth: YearMonth,
    @Schema(description = "Currency used in KPI values", example = "BRL")
    val currency: String,
    @Schema(description = "Total amount due for the period", example = "200.50")
    val totalDue: BigDecimal,
    @Schema(description = "Total amount paid for the period", example = "150.25")
    val totalPaid: BigDecimal,
    @Schema(description = "Total amount pending approval", example = "30.00")
    val totalPending: BigDecimal,
    @Schema(description = "Total unpaid amount", example = "20.25")
    val totalUnpaid: BigDecimal,
    @Schema(description = "Delinquency rate percentage", example = "10.10")
    val delinquencyRate: BigDecimal,
    @Schema(description = "Pending amount grouped by platform")
    val pendingByPlatform: List<PendingByPlatformItem>,
    @Schema(description = "Subscribers that still have debt in the reference month")
    val debtors: List<DebtorSubscriberItem>,
)
