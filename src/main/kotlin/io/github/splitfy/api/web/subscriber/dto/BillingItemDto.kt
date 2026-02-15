package io.github.splitfy.api.web.subscriber.dto

import io.github.splitfy.api.domain.enums.BillingCycle
import java.math.BigDecimal
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "Billing item for a service/platform")
data class BillingItemDto(
    val serviceId: Long,
    val serviceName: String,
    val billingCycle: BillingCycle,
    val serviceCurrency: String = "BRL",
    val serviceMonthlyAmount: BigDecimal,
    val participantsCount: Int,
    val userMonthlyShare: BigDecimal,
    val serviceMonthlyAmountOriginal: BigDecimal? = null,
    val userMonthlyShareOriginal: BigDecimal? = null,
    val exchangeRateToBrl: BigDecimal? = null,
    val exchangeRateDate: LocalDate? = null,
    val paymentStatus: PaymentStatus = PaymentStatus.UNPAID
)
