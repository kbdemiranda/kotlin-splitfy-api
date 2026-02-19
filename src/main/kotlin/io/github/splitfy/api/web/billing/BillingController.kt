package io.github.splitfy.api.web.billing

import io.github.splitfy.api.service.billing.BillingService
import io.github.splitfy.api.service.subscriber.SubscriberService
import io.github.splitfy.api.web.billing.dto.SubscriberBillingEmailRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth

@RestController
@RequestMapping("/billing")
@Tag(name = "Subscriber Billing", description = "Billing operations for subscribers")
class BillingController(
    private val billingService: BillingService,
    private val subscriberService: SubscriberService
) {

    @Operation(summary = "Get subscriber billing", description = "Returns billing details for a subscriber and an optional reference month")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Billing loaded"),
            ApiResponse(responseCode = "400", description = "Invalid reference month format"),
            ApiResponse(responseCode = "404", description = "Subscriber not found")
        ]
    )
    @GetMapping("/{id}")
    fun getBilling(
        @Parameter(description = "Subscriber ID")
        @PathVariable id: Long,
        @Parameter(description = "Reference month in format YYYY-MM, e.g. 2026-02")
        @RequestParam(required = false) referenceMonth: String?
    ): ResponseEntity<Any> {
        val month = referenceMonth?.let { YearMonth.parse(it) }
        val billing = billingService.getBillingForSubscriber(id, month)
        return ResponseEntity.ok(billing)
    }

    @Operation(summary = "Send billing summary by e-mail", description = "Sends billing summary for one or more subscribers to one or more e-mails")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Billing summary sent"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "404", description = "Subscriber not found")
        ]
    )
    @PostMapping("/email-summary")
    fun sendBillingSummary(@RequestBody request: SubscriberBillingEmailRequest): ResponseEntity<Void> {
        subscriberService.sendBillingSummaryToEmails(request)
        return ResponseEntity.ok().build()
    }
}
