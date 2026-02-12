package io.github.splitfy.api.web.subscriber.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name="Associate Request", description = "Request payload for associating platforms with a subscriber")
data class PlatformAssociationRequest(
    @Schema(description = "List of platform IDs to associate with the subscriber", example = "[1, 2, 3]")
    val platformIds: List<Long>
)
