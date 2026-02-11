package io.github.splitfy.api.service.platform

import io.github.splitfy.api.domain.entity.Platform
import io.github.splitfy.api.repository.PlatformRepository
import io.github.splitfy.api.web.platform.dto.PlatformRequest
import io.github.splitfy.api.web.platform.dto.PlatformResponse
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class PlatformService(private val platformRepository: PlatformRepository) {

    fun create(dto: PlatformRequest): PlatformResponse {
        val entity = Platform(
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
        val saved = platformRepository.save(entity)
        return toDto(saved)
    }

    fun findAll(): List<PlatformResponse> {
        return platformRepository.findAll().map { toDto(it) }
    }

    fun findById(id: Long): PlatformResponse? = getPlatform(id).let { toDto(it) }

    fun update(id: Long, dto: PlatformRequest): PlatformResponse? {
        val existing = getPlatform(id)
        val updated = existing.copy(
            platformToken = existing.platformToken,
            name = dto.name,
            price = dto.price,
            url = dto.url,
            serviceType = dto.serviceType,
            totalSlots = dto.totalSlots,
            availableSlots = dto.availableSlots,
            updatedAt = LocalDateTime.now(),
            billingCycle = dto.billingCycle,
            billingDate = dto.billingDay
        )
        val saved = platformRepository.save(updated)
        return toDto(saved)
    }

    fun delete(id: Long) {
        val platform = getPlatform(id).copy(deletedAt = LocalDateTime.now())
        platformRepository.save(platform)
    }

    fun getPlatform(id: Long): Platform {
        return platformRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Platform not found with id: $id") }
    }

    private fun toDto(entity: Platform): PlatformResponse = PlatformResponse(
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
    }
}
