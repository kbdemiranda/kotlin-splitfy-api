package io.github.splitfy.api.web.dashboard.dto

import java.math.BigDecimal

data class PendingByPlatformItem(
    val platformId: Long,
    val platformName: String,
    val pendingCount: Int,
    val pendingAmount: BigDecimal,
)
