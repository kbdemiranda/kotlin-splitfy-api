package io.github.splitfy.api.service.billing

import io.github.splitfy.api.web.subscriber.dto.BillingResponse
import java.time.YearMonth

interface BillingService {
    fun getBillingForSubscriber(subscriberId: Long, referenceMonth: YearMonth?): BillingResponse
}
