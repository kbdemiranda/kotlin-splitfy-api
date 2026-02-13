package io.github.splitfy.api.service.platform

import io.github.splitfy.api.domain.entity.Platform
import io.github.splitfy.api.exception.ResourceNotFoundApiException
import io.github.splitfy.api.repository.PlatformRepository
import io.github.splitfy.api.web.platform.dto.PlatformRequest
import io.github.splitfy.api.web.platform.dto.PlatformResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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
            currency = dto.currency,
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

    fun findAll(pageable: Pageable, name: String?): Page<PlatformResponse> {
        val platforms = if (name.isNullOrBlank()) {
            platformRepository.findByDeletedAtIsNull(pageable)
        } else {
            platformRepository.findByDeletedAtIsNullAndNameContainingIgnoreCase(name, pageable)
        }
        return platforms.map { toDto(it) }
    }

    fun findById(id: Long): PlatformResponse? = toDto(getPlatform(id))

    fun update(id: Long, dto: PlatformRequest): PlatformResponse? {
        val existing = getPlatform(id)
        val updated = existing.copy(
            platformToken = existing.platformToken,
            name = dto.name,
            price = dto.price,
            currency = dto.currency,
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
            .orElseThrow { ResourceNotFoundApiException("Platform not found with id: $id") }
    }

    private fun toDto(platform: Platform): PlatformResponse{
        return PlatformResponse(
            id = platform.id,
            name = platform.name,
            price = platform.price,
            currency = platform.currency,
            url = platform.url,
            serviceType = platform.serviceType,
            totalSlots = platform.totalSlots,
            availableSlots = platform.availableSlots,
            createdAt = platform.createdAt,
            updatedAt = platform.updatedAt,
            deletedAt = platform.deletedAt,
            billingCycle = platform.billingCycle,
            billingDay = platform.billingDate
        )
    }
}
