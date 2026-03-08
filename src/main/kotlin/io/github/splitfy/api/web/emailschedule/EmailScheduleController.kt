package io.github.splitfy.api.web.emailschedule

import io.github.splitfy.api.service.email.EmailScheduleSettingsService
import io.github.splitfy.api.web.emailschedule.dto.EmailScheduleSettingsRequest
import io.github.splitfy.api.web.emailschedule.dto.EmailScheduleSettingsResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/email-schedules")
@Tag(name = "Email Schedules", description = "Operations related to scheduled e-mail settings")
class EmailScheduleController(
    private val emailScheduleSettingsService: EmailScheduleSettingsService,
) {

    @Operation(
        summary = "Get dashboard e-mail schedule",
        description = "Returns the current configuration used to schedule the dashboard e-mail dispatch."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Schedule loaded")
        ]
    )
    @GetMapping("/dashboard")
    fun getDashboardSchedule(): ResponseEntity<EmailScheduleSettingsResponse> {
        return ResponseEntity.ok(emailScheduleSettingsService.getDashboardSchedule())
    }

    @Operation(
        summary = "Replace dashboard e-mail schedule",
        description = "Replaces the dashboard e-mail schedule configuration with the provided enabled flag, timezone and list of day/time occurrences."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Schedule updated"),
            ApiResponse(responseCode = "400", description = "Invalid request")
        ]
    )
    @PutMapping("/dashboard")
    fun updateDashboardSchedule(
        @Valid @RequestBody request: EmailScheduleSettingsRequest
    ): ResponseEntity<EmailScheduleSettingsResponse> {
        return ResponseEntity.ok(emailScheduleSettingsService.updateDashboardSchedule(request))
    }
}
