package io.github.splitfy.api.web.subscriber.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.YearMonth

@Schema(description = "Billing response for a subscriber")
data class BillingResponse(
    val userId: Long,
    val referenceMonth: YearMonth,
    val items: List<BillingItemDto>,
    val totalMonthlyDue: BigDecimal,
    val currency: Currency = Currency.BRL
)

