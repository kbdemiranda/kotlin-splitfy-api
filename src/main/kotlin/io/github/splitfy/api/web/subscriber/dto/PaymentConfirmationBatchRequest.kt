package io.github.splitfy.api.web.subscriber.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Batch request to confirm payments for a reference month")
data class PaymentConfirmationBatchRequest(
    @Schema(description = "Reference month in format YYYY-MM", example = "2026-02")
    val referenceMonth: String? = null,
    @Schema(description = "Payment confirmations grouped by subscriber")
    val confirmations: List<PaymentConfirmationItemRequest>
)
