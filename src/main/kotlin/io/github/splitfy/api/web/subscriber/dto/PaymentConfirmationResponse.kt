package io.github.splitfy.api.web.subscriber.dto

import io.github.splitfy.api.domain.enums.PaymentConfirmationStatus
import java.time.LocalDateTime
import java.time.YearMonth


data class PaymentConfirmationResponse(
    val id: Long,
    val subscriberId: Long,
    val platformId: Long,
    val referenceMonth: YearMonth,
    val status: PaymentConfirmationStatus,
    val requestedByEmail: String,
    val requestedAt: LocalDateTime,
    val validatedByEmail: String?,
    val validatedAt: LocalDateTime?
)
