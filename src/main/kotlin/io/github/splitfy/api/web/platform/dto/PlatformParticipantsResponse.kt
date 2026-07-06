package io.github.splitfy.api.web.platform.dto

import io.github.splitfy.api.domain.enums.Currency
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDateTime

@Schema(description = "Platform participants and the individual value each one pays for the service")
data class PlatformParticipantsResponse(
    @Schema(description = "Platform ID", example = "1")
    val platformId: Long,

    @Schema(description = "Platform name", example = "Netflix")
    val platformName: String,

    @Schema(description = "Original price", example = "29.90")
    val price: BigDecimal,

    @Schema(description = "Currency", example = "USD")
    val currency: Currency,

    @Schema(description = "Price converted to BRL using the latest available exchange rate")
    val priceInBrl: BigDecimal? = null,

    @Schema(description = "Number of active participants", example = "3")
    val participantsCount: Int,

    @Schema(description = "Individual share in BRL, equally split among active participants", example = "9.97")
    val individualShare: BigDecimal? = null,

    @Schema(description = "Individual share in the original service currency", nullable = true, example = "9.97")
    val individualShareOriginal: BigDecimal? = null,

    @Schema(description = "Subscribers associated with the platform")
    val participants: List<PlatformParticipantItem>
)

@Schema(description = "A subscriber associated with a platform and the individual value they pay for it")
data class PlatformParticipantItem(
    @Schema(description = "Subscriber ID", example = "6")
    val subscriberId: Long,

    @Schema(description = "Subscriber name", example = "Ana")
    val subscriberName: String,

    @Schema(description = "Subscriber email", example = "ana@example.com")
    val subscriberEmail: String,

    @Schema(description = "Date the subscriber joined the platform")
    val subscribedAt: LocalDateTime,

    @Schema(description = "Individual share in BRL", example = "9.97")
    val individualShare: BigDecimal? = null,

    @Schema(description = "Individual share in the original service currency", nullable = true, example = "9.97")
    val individualShareOriginal: BigDecimal? = null,

    @Schema(description = "Number of billing cycles with a confirmed payment since the subscriber joined", example = "3")
    val paidCyclesCount: Int = 0,

    @Schema(description = "Total amount paid so far by this subscriber, in BRL", example = "29.91")
    val totalPaid: BigDecimal? = null,

    @Schema(description = "Total amount paid so far by this subscriber, in the original service currency", nullable = true, example = "5.97")
    val totalPaidOriginal: BigDecimal? = null,

    @Schema(description = "Total amount this subscriber would have paid over the same period if subscribed alone, in BRL", example = "89.70")
    val totalIfSubscribedAlone: BigDecimal? = null,

    @Schema(description = "Total amount this subscriber would have paid over the same period if subscribed alone, in the original service currency", nullable = true, example = "17.90")
    val totalIfSubscribedAloneOriginal: BigDecimal? = null
)
