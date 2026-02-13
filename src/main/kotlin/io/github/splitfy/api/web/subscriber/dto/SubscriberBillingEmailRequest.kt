package io.github.splitfy.api.web.subscriber.dto

data class SubscriberBillingEmailRequest(
    val subscriberIds: List<Long>,
    val emails: List<String>
)
