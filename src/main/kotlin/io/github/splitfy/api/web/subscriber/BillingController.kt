package io.github.splitfy.api.web.subscriber

import io.github.splitfy.api.service.billing.BillingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth

@RestController
@RequestMapping("/subscribers")
@Tag(name = "Subscriber Billing", description = "Billing operations for subscribers")
class BillingController(
    private val billingService: BillingService
) {

    @Operation(summary = "Get billing for a subscriber for a given month")
    @GetMapping("/{id}/billing")
    fun getBilling(
        @PathVariable id: Long,
        @Parameter(description = "Reference month in format YYYY-MM, e.g. 2026-02")
        @RequestParam(required = false) referenceMonth: String?
    ): ResponseEntity<Any> {
        val month = referenceMonth?.let { YearMonth.parse(it) }
        val billing = billingService.getBillingForSubscriber(id, month)
        return ResponseEntity.ok(billing)
    }
}

