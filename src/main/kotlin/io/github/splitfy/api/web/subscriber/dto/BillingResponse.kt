package io.github.splitfy.api.web.subscriber.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.YearMonth

@Schema(description = "Billing response for a subscriber")
data class BillingResponse(
    @Schema(description = "Subscriber ID", example = "1")
    val userId: Long,
    @Schema(description = "Reference month", example = "2026-02")
    val referenceMonth: YearMonth,
    @Schema(description = "Billing items for each platform")
    val items: List<BillingItemDto>,
    @Schema(description = "Total monthly amount due for the subscriber", example = "29.90")
    val totalMonthlyDue: BigDecimal,
    @Schema(description = "Currency used in totals", example = "BRL")
    val currency: Currency = Currency.BRL
)
