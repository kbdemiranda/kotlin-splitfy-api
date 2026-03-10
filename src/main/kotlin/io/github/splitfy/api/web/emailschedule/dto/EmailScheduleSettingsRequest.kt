package io.github.splitfy.api.web.emailschedule.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank

@Schema(description = "Request payload to replace the KPI summary e-mail schedule settings")
data class EmailScheduleSettingsRequest(
    @field:Schema(description = "Whether the schedule is enabled", example = "true")
    val enabled: Boolean,

    @field:NotBlank
    @field:Schema(description = "Timezone used to evaluate the schedule", example = "America/Sao_Paulo")
    val timezone: String,

    @field:Valid
    @field:Schema(description = "Configured occurrences for the schedule")
    val occurrences: List<EmailScheduleOccurrenceRequest>,
)
