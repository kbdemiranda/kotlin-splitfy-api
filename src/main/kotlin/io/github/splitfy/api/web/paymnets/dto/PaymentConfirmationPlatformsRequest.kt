package io.github.splitfy.api.web.paymnets.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request body with platform ids to register as paid")
data class PaymentConfirmationPlatformsRequest(
    @Schema(description = "Reference month in format YYYY-MM", example = "2026-01")
    val referenceMonth: String,
    @Schema(description = "List of platform/service ids paid by subscriber", example = "[1, 2]")
    val platformIds: List<Long>
)
