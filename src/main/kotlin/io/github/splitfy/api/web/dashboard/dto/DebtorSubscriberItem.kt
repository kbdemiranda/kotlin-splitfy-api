package io.github.splitfy.api.web.dashboard.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "Subscriber with outstanding debt in the reference month")
data class DebtorSubscriberItem(
    @Schema(description = "Subscriber ID", example = "12")
    val subscriberId: Long,
    @Schema(description = "Subscriber name", example = "John Doe")
    val subscriberName: String,
    @Schema(description = "Subscriber e-mail", example = "john.doe@example.com")
    val subscriberEmail: String,
    @Schema(description = "Amount pending approval", example = "19.90")
    val pendingAmount: BigDecimal,
    @Schema(description = "Amount still unpaid (without payment confirmation)", example = "39.80")
    val unpaidAmount: BigDecimal,
    @Schema(description = "Total debt for the reference month", example = "59.70")
    val totalDebt: BigDecimal,
)
