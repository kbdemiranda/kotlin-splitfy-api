package io.github.splitfy.api.web.emailschedule.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Dashboard e-mail schedule settings")
data class EmailScheduleSettingsResponse(
    @Schema(description = "Internal key of the configured schedule", example = "DASHBOARD_EMAIL")
    val scheduleKey: String,

    @Schema(description = "Whether the schedule is enabled", example = "true")
    val enabled: Boolean,

    @Schema(description = "Timezone used to evaluate the schedule", example = "America/Sao_Paulo")
    val timezone: String,

    @Schema(description = "Configured day/time occurrences")
    val occurrences: List<EmailScheduleOccurrenceResponse>,
)
