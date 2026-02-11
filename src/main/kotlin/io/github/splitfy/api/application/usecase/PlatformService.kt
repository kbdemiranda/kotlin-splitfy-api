package io.github.splitfy.api.application.usecase

import io.github.splitfy.api.application.dto.input.PlatformIn
import io.github.splitfy.api.application.dto.output.PlatformOut
import io.github.splitfy.api.application.mapper.PlatformMapper
import io.github.splitfy.api.domain.models.Platform
import io.github.splitfy.api.domain.repository.PlatformRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class PlatformService(private val platformRepository: PlatformRepository) {

    fun create(dto: PlatformIn): PlatformOut {
        val entity = PlatformMapper.toEntity(dto.copy())
        val saved = platformRepository.save(entity)
        return PlatformMapper.toDto(saved)
    }

    fun findAll(): List<PlatformOut> = platformRepository.findAll().map { PlatformMapper.toDto(it) }

    fun findById(id: Long): PlatformOut? = getPlatform(id).let { PlatformMapper.toDto(it) }

    fun update(id: Long, dto: PlatformIn): PlatformOut? {
        val updated = getPlatform(id).copy(
            name = dto.name,
            price = dto.price,
            url = dto.url,
            serviceType = dto.serviceType,
            totalSlots = dto.totalSlots,
            availableSlots = dto.availableSlots,
            updatedAt = java.time.LocalDateTime.now(),
            billingCycle = dto.billingCycle,
            billingDate = dto.billingDay
        )
        return PlatformMapper.toDto(platformRepository.save(updated))
    }

    fun delete(id: Long) {
        val platform = getPlatform(id).copy(deletedAt = LocalDateTime.now())
        platformRepository.save(platform);
    }


    fun getPlatform(id: Long): Platform {
        return platformRepository.findById(id).orElseThrow { EntityNotFoundException("Platform not found with id: $id") }
    }
}

