package io.github.splitfy.api.web.emailschedule.dto

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.time.LocalTime

@Schema(description = "Single day/time occurrence for an e-mail schedule")
data class EmailScheduleOccurrenceRequest(
    @field:NotNull
    @field:Min(1)
    @field:Max(7)
    @field:Schema(description = "Day of week where 1=Monday and 7=Sunday", example = "1")
    val dayOfWeek: Int,

    @field:NotNull
    @field:JsonFormat(pattern = "HH:mm")
    @field:Schema(description = "Execution time in HH:mm", example = "10:00", type = "string")
    val executionTime: LocalTime,
)
