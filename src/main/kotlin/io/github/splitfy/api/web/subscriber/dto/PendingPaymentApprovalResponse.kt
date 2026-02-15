package io.github.splitfy.api.web.subscriber.dto

import io.github.splitfy.api.domain.enums.Currency
import io.github.splitfy.api.domain.enums.PaymentConfirmationStatus
import io.github.splitfy.api.domain.enums.ServiceType
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.YearMonth

data class PendingPaymentApprovalResponse(
    val confirmationId: Long,
    val referenceMonth: YearMonth,
    val status: PaymentConfirmationStatus,
    val requestedByEmail: String,
    val requestedAt: LocalDateTime,
    val subscriber: PendingPaymentSubscriber,
    val platform: PendingPaymentPlatform
)

data class PendingPaymentSubscriber(
    val id: Long,
    val name: String,
    val email: String
)

data class PendingPaymentPlatform(
    val id: Long,
    val name: String,
    val serviceType: ServiceType,
    val currency: Currency,
    val price: BigDecimal
)
