package io.github.splitfy.api.web.billing.dto

import io.github.splitfy.api.domain.enums.BillingCycle
import java.math.BigDecimal
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "Billing item for a service/platform")
data class BillingItemDto(
    @Schema(description = "Platform ID", example = "1")
    val serviceId: Long,
    @Schema(description = "Platform name", example = "Netflix")
    val serviceName: String,
    @Schema(description = "Platform billing cycle")
    val billingCycle: BillingCycle,
    @Schema(description = "Platform currency", example = "BRL")
    val serviceCurrency: String = "BRL",
    @Schema(description = "Total monthly amount of the platform in BRL", example = "59.90")
    val serviceMonthlyAmount: BigDecimal,
    @Schema(description = "Number of participants sharing the platform", example = "3")
    val participantsCount: Int,
    @Schema(description = "Subscriber monthly share in BRL", example = "19.97")
    val userMonthlyShare: BigDecimal,
    @Schema(description = "Original total monthly amount in platform currency", nullable = true, example = "9.99")
    val serviceMonthlyAmountOriginal: BigDecimal? = null,
    @Schema(description = "Original subscriber monthly share in platform currency", nullable = true, example = "3.33")
    val userMonthlyShareOriginal: BigDecimal? = null,
    @Schema(description = "Exchange rate to BRL used in the conversion", nullable = true, example = "5.25")
    val exchangeRateToBrl: BigDecimal? = null,
    @Schema(description = "Date of the exchange rate used for conversion", nullable = true)
    val exchangeRateDate: LocalDate? = null,
    @Schema(description = "Subscriber payment status for this platform")
    val paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
    @Schema(description = "Covered subscribers that compose this billed amount")
    val coveredSubscribers: List<BillingCoveredSubscriberItemDto> = emptyList()
)
