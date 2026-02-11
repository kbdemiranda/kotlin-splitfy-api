package io.github.splitfy.api.application.usecase

import io.github.splitfy.api.application.dto.input.SubscriberIn
import io.github.splitfy.api.application.dto.output.SubscriberOut
import io.github.splitfy.api.application.mapper.SubscriberMapper
import io.github.splitfy.api.domain.models.Subscriber
import io.github.splitfy.api.domain.repository.SubscriberRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class SubscriberService (private val subscriberRepository: SubscriberRepository) {

    fun create(dto: SubscriberIn): SubscriberOut {
        val entity = SubscriberMapper.toEntity(dto.copy())
        val saved = subscriberRepository.save(entity)
        return SubscriberMapper.toDto(saved)
    }

    fun list(): List<SubscriberOut> {
        return subscriberRepository.findAll().map { SubscriberMapper.toDto(it) }
    }

    fun get(id: Long): SubscriberOut? {
        return getSubscriber(id).let { SubscriberMapper.toDto(it) }
    }

    fun update(id: Long, dto: SubscriberIn): SubscriberOut? {
        val updated = getSubscriber(id).copy(
            name = dto.name,
            email = dto.email,
            updatedAt = LocalDateTime.now()
        )
        val saved = subscriberRepository.save(updated)
        return SubscriberMapper.toDto(saved)
    }

    fun delete(id: Long) {
        val subscriber = getSubscriber(id).copy(deletedAt = LocalDateTime.now())
        subscriberRepository.save(subscriber)
    }

    private fun getSubscriber(id: Long): Subscriber {
        return subscriberRepository.findById(id).orElseThrow { EntityNotFoundException("Subscriber not found with id: $id") }
    }
}
