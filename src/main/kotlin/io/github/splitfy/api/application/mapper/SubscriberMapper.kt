package io.github.splitfy.api.application.mapper

import io.github.splitfy.api.application.dto.input.SubscriberIn
import io.github.splitfy.api.application.dto.output.SubscriberOut
import io.github.splitfy.api.domain.models.Subscriber
import java.time.LocalDateTime
import java.util.UUID

object SubscriberMapper {
    fun toEntity(dto: SubscriberIn): Subscriber {
        return Subscriber(
            subscriberToken = UUID.randomUUID(),
            name = dto.name,
            email = dto.email,
            createdAt = LocalDateTime.now(),
        )
    }

    fun toDto(entity: Subscriber): SubscriberOut {
        return SubscriberOut(
            id = entity.id,
            name = entity.name,
            email = entity.email,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            deletedAt = entity.deletedAt,
        )
    }
}