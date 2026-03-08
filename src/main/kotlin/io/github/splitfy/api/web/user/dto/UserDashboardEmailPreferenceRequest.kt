package io.github.splitfy.api.web.user.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request payload to update whether a user receives the scheduled dashboard e-mail")
data class UserDashboardEmailPreferenceRequest(
    @field:Schema(
        description = "Whether this user should receive the scheduled dashboard e-mail",
        example = "true"
    )
    val receivesDashboardEmail: Boolean,

    @field:Schema(
        description = "When true, allows replacing the current active recipient if another user is already selected",
        example = "false"
    )
    val force: Boolean = false,
)
