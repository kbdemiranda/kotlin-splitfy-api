package io.github.splitfy.api.web.dashboard.dto

import java.math.BigDecimal
import java.time.YearMonth

data class DashboardKpiResponse(
    val referenceMonth: YearMonth,
    val currency: String,
    val totalDue: BigDecimal,
    val totalPaid: BigDecimal,
    val totalPending: BigDecimal,
    val totalUnpaid: BigDecimal,
    val delinquencyRate: BigDecimal,
    val pendingByPlatform: List<PendingByPlatformItem>,
)
