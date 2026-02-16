package io.github.splitfy.api.web.subscriber

import io.github.splitfy.api.service.subscriber.SubscriberService
import io.github.splitfy.api.web.subscriber.dto.PlatformAssociationRequest
import io.github.splitfy.api.web.subscriber.dto.SubscriberBillingEmailRequest
import io.github.splitfy.api.web.subscriber.dto.SubscriberRequest
import io.github.splitfy.api.web.subscriber.dto.SubscriberResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/subscribers")
@Tag(name = "Subscribers", description = "Operations related to subscribers")
class SubscriberController (private val subscriberService: SubscriberService) {

    @Operation(summary = "List subscribers", description = "Returns all subscribers")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "OK")
        ]
    )
    @GetMapping
    fun list(
        @Parameter(description = "Filter by name (partial match, case-insensitive)")
        @RequestParam(required = false) name: String?,
        @PageableDefault(page = 0, size = 10, sort = ["name"]) pageable: Pageable
    ): ResponseEntity<Page<SubscriberResponse>> {
        val subscribers = subscriberService.list(pageable, name)
        return ResponseEntity.ok(subscribers)
    }

    @Operation(summary = "Get subscriber", description = "Returns a subscriber by ID")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Found"),
            ApiResponse(responseCode = "404", description = "Not found")
        ]
    )
    @GetMapping("/{id}")
    fun get(@Parameter(description = "Subscriber ID") @PathVariable id: Long): ResponseEntity<SubscriberResponse> {
        val subscriber = subscriberService.get(id)
        return ResponseEntity.ok(subscriber)
    }

    @Operation(summary = "Create subscriber", description = "Creates a new subscriber")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Created"),
            ApiResponse(responseCode = "400", description = "Invalid request")
        ]
    )
    @PostMapping
    fun create(@RequestBody subscriberIn: SubscriberRequest): ResponseEntity<SubscriberResponse> {
        val subscriber = subscriberService.create(subscriberIn)
        return ResponseEntity.ok(subscriber)
    }

    @Operation(summary = "Update subscriber", description = "Updates a subscriber by ID")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Updated"),
            ApiResponse(responseCode = "404", description = "Not found")
        ]
    )
    @PutMapping("/{id}")
    fun update(
        @Parameter(description = "Subscriber ID") @PathVariable id: Long,
        @RequestBody subscriberIn: SubscriberRequest
    ): ResponseEntity<SubscriberResponse> {
        val subscriber = subscriberService.update(id, subscriberIn)
        return ResponseEntity.ok(subscriber)
    }

    @Operation(summary = "Delete subscriber", description = "Deletes a subscriber by ID")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Deleted"),
            ApiResponse(responseCode = "404", description = "Not found")
        ]
    )
    @DeleteMapping("/{id}")
    fun delete(@Parameter(description = "Subscriber ID") @PathVariable id: Long): ResponseEntity<Void> {
        subscriberService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Associate platforms", description = "Associates one or more platforms with a subscriber")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Associated"),
            ApiResponse(responseCode = "400", description = "Invalid platform IDs"),
            ApiResponse(responseCode = "404", description = "Not found")
        ]
    )
    @PostMapping("/{id}/associate")
    fun associate(
        @Parameter(description = "Subscriber ID") @PathVariable id: Long,
        @RequestBody platformAssociationRequest: List<PlatformAssociationRequest>
    ): ResponseEntity<Void>{
        subscriberService.associatePlatforms(id, platformAssociationRequest)
        return ResponseEntity.ok().build<Void>()
    }

    @Operation(summary = "Disassociate platforms", description = "Removes one or more platform associations from a subscriber")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Disassociated"),
            ApiResponse(responseCode = "400", description = "Invalid platform IDs"),
            ApiResponse(responseCode = "404", description = "Not found")
        ]
    )
    @PutMapping("/{id}/disassociate")
    fun disassociate(
        @Parameter(description = "Subscriber ID") @PathVariable id: Long,
        @RequestBody platformAssociationRequest: List<PlatformAssociationRequest>
    ): ResponseEntity<Void>{
        subscriberService.disassociatePlatforms(id, platformAssociationRequest);
        return ResponseEntity.ok().build<Void>();
    }

    @Operation(summary = "Send billing summary by e-mail", description = "Sends billing summary for one or more subscribers to one or more e-mails")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Billing summary sent"),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "404", description = "Subscriber not found")
        ]
    )
    @PostMapping("/billing/email-summary")
    fun sendBillingSummary(@RequestBody request: SubscriberBillingEmailRequest): ResponseEntity<Void> {
        subscriberService.sendBillingSummaryToEmails(request)
        return ResponseEntity.ok().build()
    }

}
