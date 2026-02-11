package io.github.splitfy.api.application.mapper

import io.github.splitfy.api.application.dto.input.PlatformIn
import io.github.splitfy.api.application.dto.output.PlatformOut
import io.github.splitfy.api.domain.models.Platform
import java.time.LocalDateTime
import java.util.UUID

object PlatformMapper {
    fun toDto(entity: Platform): PlatformOut = PlatformOut(
        id = entity.id,
        name = entity.name,
        price = entity.price,
        url = entity.url,
        serviceType = entity.serviceType,
        totalSlots = entity.totalSlots,
        availableSlots = entity.availableSlots,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        deletedAt = entity.deletedAt,
        billingCycle = entity.billingCycle,
        billingDay = entity.billingDate
    )

    fun toEntity(dto: PlatformIn): Platform = Platform(
        platformToken = UUID.randomUUID(),
        name = dto.name,
        price = dto.price,
        url = dto.url,
        serviceType = dto.serviceType,
        totalSlots = dto.totalSlots,
        availableSlots = dto.availableSlots,
        createdAt = LocalDateTime.now(),
        billingCycle = dto.billingCycle,
        billingDate = dto.billingDay
    )
}

