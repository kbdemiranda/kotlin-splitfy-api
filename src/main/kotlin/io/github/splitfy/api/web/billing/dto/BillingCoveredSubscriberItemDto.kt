package io.github.splitfy.api.web.billing.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "Billing share charged for a covered subscriber")
data class BillingCoveredSubscriberItemDto(
    @Schema(description = "Covered subscriber ID", example = "6")
    val subscriberId: Long,
    @Schema(description = "Covered subscriber name", example = "Ana")
    val subscriberName: String,
    @Schema(description = "Covered subscriber share in BRL", example = "19.97")
    val monthlyShare: BigDecimal,
    @Schema(description = "Covered subscriber share in original service currency", nullable = true, example = "3.33")
    val monthlyShareOriginal: BigDecimal? = null,
    @Schema(description = "Payment status for the covered subscriber share")
    val paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
)
