package io.github.splitfy.api.web.billing.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Billing summary e-mail request payload")
data class SubscriberBillingEmailRequest(
    @Schema(description = "Subscriber IDs that will be included in the billing summary", example = "[1, 2]")
    val subscriberIds: List<Long>,
    @Schema(description = "Destination e-mail addresses", example = "[\"finance@example.com\", \"admin@example.com\"]")
    val emails: List<String>,
    @Schema(description = "Reference month in format YYYY-MM. If omitted, current month is used", example = "2026-02")
    val referenceMonth: String? = null
)
