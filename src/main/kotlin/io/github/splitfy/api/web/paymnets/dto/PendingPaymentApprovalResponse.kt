package io.github.splitfy.api.web.paymnets.dto

import io.github.splitfy.api.domain.enums.Currency
import io.github.splitfy.api.domain.enums.PaymentConfirmationStatus
import io.github.splitfy.api.domain.enums.ServiceType
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.YearMonth

@Schema(description = "Pending payment confirmation awaiting approval")
data class PendingPaymentApprovalResponse(
    @Schema(description = "Payment confirmation ID", example = "1")
    val confirmationId: Long,
    @Schema(description = "Reference month", example = "2026-02")
    val referenceMonth: YearMonth,
    @Schema(description = "Current confirmation status")
    val status: PaymentConfirmationStatus,
    @Schema(description = "E-mail of the user who requested the confirmation", example = "john.doe@example.com")
    val requestedByEmail: String,
    @Schema(description = "Date and time when confirmation was requested")
    val requestedAt: LocalDateTime,
    @Schema(description = "Subscriber details")
    val subscriber: PendingPaymentSubscriber,
    @Schema(description = "Platform details")
    val platform: PendingPaymentPlatform
)

@Schema(description = "Subscriber details for pending payment approval")
data class PendingPaymentSubscriber(
    @Schema(description = "Subscriber ID", example = "1")
    val id: Long,
    @Schema(description = "Subscriber name", example = "John Doe")
    val name: String,
    @Schema(description = "Subscriber e-mail", example = "john.doe@example.com")
    val email: String
)

@Schema(description = "Platform details for pending payment approval")
data class PendingPaymentPlatform(
    @Schema(description = "Platform ID", example = "1")
    val id: Long,
    @Schema(description = "Platform name", example = "Netflix")
    val name: String,
    @Schema(description = "Platform service type")
    val serviceType: ServiceType,
    @Schema(description = "Platform currency")
    val currency: Currency,
    @Schema(description = "Platform price", example = "19.90")
    val price: BigDecimal
)
