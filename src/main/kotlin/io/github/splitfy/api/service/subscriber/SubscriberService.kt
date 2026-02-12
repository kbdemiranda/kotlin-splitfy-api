package io.github.splitfy.api.service.subscriber

import io.github.splitfy.api.domain.entity.Subscriber
import io.github.splitfy.api.domain.entity.SubscriberPlatform
import io.github.splitfy.api.repository.PlatformRepository
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.repository.SubscriberRepository
import io.github.splitfy.api.web.subscriber.dto.PlatformAssociationRequest
import io.github.splitfy.api.web.subscriber.dto.SubscriberRequest
import io.github.splitfy.api.web.subscriber.dto.SubscriberResponse
import jakarta.persistence.EntityNotFoundException
import org.apache.coyote.BadRequestException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class SubscriberService(
    private val subscriberRepository: SubscriberRepository,
    private val platformRepository: PlatformRepository,
    private val subscriberPlatformRepository: SubscriberPlatformRepository
) {

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

    fun list(pageable: Pageable, name: String?): Page<SubscriberResponse> {
        val subscribers = if (name.isNullOrBlank()) {
            subscriberRepository.findByDeletedAtIsNull(pageable)
        } else {
            subscriberRepository.findByDeletedAtIsNullAndNameContainingIgnoreCase(name, pageable)
        }
        return subscribers.map { toDto(it) }
    }

    fun get(id: Long): SubscriberResponse? {
        return toDto(getSubscriber(id))
    }

    @PreAuthorize("hasRole('ADMIN') or (hasRole('EDITOR') and @subscriberSecurity.isOwner(#id, authentication.name))")
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

    @PreAuthorize("hasRole('ADMIN') or (hasRole('EDITOR') and @subscriberSecurity.isOwner(#id, authentication.name))")
    fun associatePlatforms(id: Long, platformAssociationRequest: List<PlatformAssociationRequest>) {
        if (platformAssociationRequest.isEmpty()) return

        val platformIds = platformAssociationRequest.flatMap { it.platformIds }.toSet()
        if (platformIds.isEmpty()) return

        val subscriber = getSubscriber(id)

        for (platformId in platformIds) {
            val platform = platformRepository.findById(platformId)
                .orElseThrow { EntityNotFoundException("Platform not found with id: $platformId") }

            if (platform.deletedAt != null) {
                throw EntityNotFoundException("Platform not found with id: $platformId")
            }

            if (platform.availableSlots <= 0) {
                throw BadRequestException("Plataforma sem vagas disponíveis")
            }

            val existingAssociation = subscriber.id?.let {
                subscriberPlatformRepository.findBySubscriberIdAndPlatformId(it, platformId)
            }

            when {
                existingAssociation == null -> {
                    val association = SubscriberPlatform(
                        subscriber = subscriber,
                        platform = platform,
                        createdAt = LocalDateTime.now()
                    )
                    subscriberPlatformRepository.save(association)
                }
                existingAssociation.isActive && existingAssociation.deletedAt == null -> {
                    throw BadRequestException("Subscriber already associated with platform id: $platformId")
                }
                else -> {
                    val reactivatedAssociation = existingAssociation.copy(
                        isActive = true,
                        unsubscribedAt = null,
                        updatedAt = LocalDateTime.now(),
                        deletedAt = null
                    )
                    subscriberPlatformRepository.save(reactivatedAssociation)
                }
            }

            val updatedPlatform = platform.copy(
                availableSlots = platform.availableSlots - 1,
                updatedAt = LocalDateTime.now()
            )
            platformRepository.save(updatedPlatform)
        }
    }

    @PreAuthorize("hasRole('ADMIN') or (hasRole('EDITOR') and @subscriberSecurity.isOwner(#id, authentication.name))")
    fun disassociatePlatforms(id: Long, platformAssociationRequest: List<PlatformAssociationRequest>) {
        if (platformAssociationRequest.isEmpty()) return

        val platformIds = platformAssociationRequest.flatMap { it.platformIds }.toSet()
        if (platformIds.isEmpty()) return

        val subscriber = getSubscriber(id)

        for (platformId in platformIds) {
            val platform = platformRepository.findById(platformId)
                .orElseThrow { EntityNotFoundException("Platform not found with id: $platformId") }

            if (platform.deletedAt != null) {
                throw EntityNotFoundException("Platform not found with id: $platformId")
            }

            val association = subscriber.id?.let {
                subscriberPlatformRepository.findBySubscriberIdAndPlatformId(it, platformId)
            }

            if (association == null || !association.isActive || association.deletedAt != null) {
                throw BadRequestException("Subscriber not associated with platform id: $platformId")
            }

            val updatedAssociation = association.copy(
                isActive = false,
                unsubscribedAt = LocalDateTime.now(),
                deletedAt = LocalDateTime.now()
            )
            subscriberPlatformRepository.save(updatedAssociation)

            val updatedPlatform = platform.copy(
                availableSlots = platform.availableSlots + 1,
                updatedAt = LocalDateTime.now()
            )
            platformRepository.save(updatedPlatform)
        }
    }
}
