package io.github.splitfy.api.web.dashboard.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "Pending payment metrics for a specific platform")
data class PendingByPlatformItem(
    @Schema(description = "Platform ID", example = "1")
    val platformId: Long,
    @Schema(description = "Platform name", example = "Netflix")
    val platformName: String,
    @Schema(description = "Number of pending payments", example = "3")
    val pendingCount: Int,
    @Schema(description = "Pending amount for the platform", example = "59.70")
    val pendingAmount: BigDecimal,
)
