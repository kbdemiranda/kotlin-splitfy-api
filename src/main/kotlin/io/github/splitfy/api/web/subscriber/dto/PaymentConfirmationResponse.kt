package io.github.splitfy.api.web.subscriber.dto

import io.github.splitfy.api.domain.enums.PaymentConfirmationStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.time.YearMonth

@Schema(description = "Payment confirmation response payload")
data class PaymentConfirmationResponse(
    @Schema(description = "Payment confirmation ID", example = "1")
    val id: Long,
    @Schema(description = "Subscriber ID", example = "1")
    val subscriberId: Long,
    @Schema(description = "Platform ID", example = "2")
    val platformId: Long,
    @Schema(description = "Reference month", example = "2026-02")
    val referenceMonth: YearMonth,
    @Schema(description = "Payment confirmation status")
    val status: PaymentConfirmationStatus,
    @Schema(description = "E-mail of the user who requested confirmation", example = "john.doe@example.com")
    val requestedByEmail: String,
    @Schema(description = "Date and time when confirmation was requested")
    val requestedAt: LocalDateTime,
    @Schema(description = "E-mail of the approver user", nullable = true)
    val validatedByEmail: String?,
    @Schema(description = "Date and time when confirmation was approved", nullable = true)
    val validatedAt: LocalDateTime?
)
