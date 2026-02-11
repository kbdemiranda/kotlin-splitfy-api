package io.github.splitfy.api.application.mapper

import io.github.splitfy.api.application.dto.PlatformDto
import io.github.splitfy.api.domain.models.Platform

object PlatformMapper {
    fun toDto(entity: Platform): PlatformDto = PlatformDto(
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

    fun toEntity(dto: PlatformDto): Platform = Platform(
        id = dto.id,
        name = dto.name,
        price = dto.price,
        url = dto.url,
        serviceType = dto.serviceType,
        totalSlots = dto.totalSlots,
        availableSlots = dto.availableSlots,
        createdAt = dto.createdAt ?: java.time.LocalDateTime.now(),
        updatedAt = dto.updatedAt,
        deletedAt = dto.deletedAt,
        billingCycle = dto.billingCycle,
        billingDate = dto.billingDay
    )
}

