package io.github.splitfy.api.web.paymnets

import io.github.splitfy.api.service.payment.PaymentConfirmationService
import io.github.splitfy.api.web.paymnets.dto.PaymentConfirmationBatchRequest
import io.github.splitfy.api.web.paymnets.dto.PaymentConfirmationResponse
import io.github.splitfy.api.web.paymnets.dto.PendingPaymentApprovalResponse
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

@RestController
@RequestMapping("/paymnets")
@Tag(name = "Payment Confirmations", description = "Confirm and approve monthly payments")
class PaymentConfirmationController(
    private val paymentConfirmationService: PaymentConfirmationService
) {

    @Operation(summary = "Register payment confirmations", description = "Registers monthly payment confirmations sent by subscribers")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Payment confirmations registered"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "403", description = "Forbidden")
        ]
    )
    @PostMapping
    fun confirmPayments(@RequestBody request: PaymentConfirmationBatchRequest): ResponseEntity<List<PaymentConfirmationResponse>> {
        val result = paymentConfirmationService.createConfirmations(request)
        return ResponseEntity.ok(result)
    }

    @Operation(summary = "Approve payment confirmation", description = "Approves a pending payment confirmation (admin only)")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Payment confirmation approved"),
            ApiResponse(responseCode = "404", description = "Payment confirmation not found")
        ]
    )
    @PostMapping("/{id}/approve")
    fun approve(@Parameter(description = "Payment confirmation ID") @PathVariable id: Long): ResponseEntity<PaymentConfirmationResponse> {
        return ResponseEntity.ok(paymentConfirmationService.approveConfirmation(id))
    }

    @Operation(summary = "List pending payment confirmations", description = "Lists pending payment confirmations for approval (admin only)")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Pending confirmations listed")
        ]
    )
    @GetMapping("/pending")
    fun listPending(
        @Parameter(description = "Optional reference month in format YYYY-MM")
        @RequestParam(required = false) referenceMonth: String?
    ): ResponseEntity<List<PendingPaymentApprovalResponse>> {
        val response = paymentConfirmationService.listPendingConfirmations(referenceMonth)
        return ResponseEntity.ok(response)
    }
}
