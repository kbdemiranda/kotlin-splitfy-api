package io.github.splitfy.api.application.usecase

import io.github.splitfy.api.application.dto.PlatformDto
import io.github.splitfy.api.application.mapper.PlatformMapper
import io.github.splitfy.api.domain.repository.PlatformRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class PlatformService(private val repository: PlatformRepository) {

    fun create(dto: PlatformDto): PlatformDto {
        val entity = PlatformMapper.toEntity(dto.copy(id = null))
        val saved = repository.save(entity)
        return PlatformMapper.toDto(saved)
    }

    fun findAll(): List<PlatformDto> = repository.findAll().map { PlatformMapper.toDto(it) }

    fun findById(id: Long): PlatformDto? = repository.findByIdOrNull(id)?.let { PlatformMapper.toDto(it) }

    fun update(id: Long, dto: PlatformDto): PlatformDto? {
        val existing = repository.findByIdOrNull(id) ?: return null
        val updated = existing.copy(
            name = dto.name,
            price = dto.price,
            url = dto.url,
            serviceType = dto.serviceType,
            totalSlots = dto.totalSlots,
            availableSlots = dto.availableSlots,
            updatedAt = java.time.LocalDateTime.now(),
            deletedAt = dto.deletedAt,
            billingCycle = dto.billingCycle,
            billingDate = dto.billingDay
        )
        return PlatformMapper.toDto(repository.save(updated))
    }

    fun delete(id: Long): Boolean {
        if (!repository.existsById(id)) return false
        repository.deleteById(id)
        return true
    }
}

