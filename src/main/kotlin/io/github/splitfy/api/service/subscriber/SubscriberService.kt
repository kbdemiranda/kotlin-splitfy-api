package io.github.splitfy.api.service.subscriber

import io.github.splitfy.api.domain.entity.Subscriber
import io.github.splitfy.api.repository.SubscriberRepository
import io.github.splitfy.api.web.subscriber.dto.SubscriberRequest
import io.github.splitfy.api.web.subscriber.dto.SubscriberResponse
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class SubscriberService(private val subscriberRepository: SubscriberRepository) {

    fun create(dto: SubscriberRequest): SubscriberResponse {
        val entity = Subscriber(
            subscriberToken = UUID.randomUUID(),
            name = dto.name,
            email = dto.email,
            createdAt = LocalDateTime.now(),
        )
        val saved = subscriberRepository.save(entity)
        return toDto(saved)
    }

    fun list(): List<SubscriberResponse> {
        return subscriberRepository.findAll().map { toDto(it) }
    }

    fun get(id: Long): SubscriberResponse? {
        return getSubscriber(id).let { toDto(it) }
    }

    fun update(id: Long, dto: SubscriberRequest): SubscriberResponse? {
        val updated = getSubscriber(id).copy(
            name = dto.name,
            email = dto.email,
            updatedAt = LocalDateTime.now()
        )
        val saved = subscriberRepository.save(updated)
        return toDto(saved)
    }

    fun delete(id: Long) {
        val subscriber = getSubscriber(id).copy(deletedAt = LocalDateTime.now())
        subscriberRepository.save(subscriber)
    }

    private fun getSubscriber(id: Long): Subscriber {
        return subscriberRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Subscriber not found with id: $id") }
    }

    private fun toDto(entity: Subscriber): SubscriberResponse {
        return SubscriberResponse(
            id = entity.id,
            name = entity.name,
            email = entity.email,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            deletedAt = entity.deletedAt,
        )
    }
}
