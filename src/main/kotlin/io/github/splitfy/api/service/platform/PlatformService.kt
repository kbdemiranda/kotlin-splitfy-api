package io.github.splitfy.api.service.platform

import io.github.splitfy.api.domain.entity.Platform
import io.github.splitfy.api.domain.enums.Currency
import io.github.splitfy.api.exception.ResourceNotFoundApiException
import io.github.splitfy.api.repository.PlatformRepository
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.service.exchange.ExchangeRateQuote
import io.github.splitfy.api.service.exchange.ExchangeRateService
import io.github.splitfy.api.logging.infoEvent
import io.github.splitfy.api.web.platform.dto.PlatformParticipantItem
import io.github.splitfy.api.web.platform.dto.PlatformParticipantsResponse
import io.github.splitfy.api.web.platform.dto.PlatformRequest
import io.github.splitfy.api.web.platform.dto.PlatformResponse
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class PlatformService(
    private val platformRepository: PlatformRepository,
    private val exchangeRateService: ExchangeRateService,
    private val subscriberPlatformRepository: SubscriberPlatformRepository
) {
    private val log = LoggerFactory.getLogger(PlatformService::class.java)
    private val FINAL_SCALE = 2
    private val INTERMEDIATE_SCALE = 10
    private val ROUNDING = RoundingMode.HALF_UP

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
        log.infoEvent("crud.create", "entity" to "platform", "entityId" to saved.id, "entityToken" to saved.platformToken, "name" to saved.name)
        return toDto(saved, null)
    }

    fun findAll(pageable: Pageable, name: String?): Page<PlatformResponse> {
        val platforms = if (name.isNullOrBlank()) {
            platformRepository.findByDeletedAtIsNull(pageable)
        } else {
            platformRepository.findByDeletedAtIsNullAndNameContainingIgnoreCase(name, pageable)
        }
        val currencies = platforms.content
            .map { it.currency }
            .filter { it != Currency.BRL }
            .distinct()
        val ratesByCurrency = currencies.associateWith { exchangeRateService.getLatestBrlRate(it) }
        log.infoEvent("crud.list", "entity" to "platform", "page" to pageable.pageNumber, "size" to pageable.pageSize, "filterName" to name, "resultCount" to platforms.numberOfElements)
        return platforms.map { toDto(it, ratesByCurrency[it.currency]) }
    }

    fun findById(id: Long): PlatformResponse? {
        val platform = getPlatform(id)
        val quote = if (platform.currency == Currency.BRL) null else exchangeRateService.getLatestBrlRate(platform.currency)
        log.infoEvent("crud.get", "entity" to "platform", "entityId" to platform.id, "entityToken" to platform.platformToken, "name" to platform.name)
        return toDto(platform, quote)
    }

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
        log.infoEvent("crud.update", "entity" to "platform", "entityId" to saved.id, "entityToken" to saved.platformToken, "name" to saved.name)
        return toDto(saved, null)
    }

    fun delete(id: Long) {
        val platform = getPlatform(id).copy(deletedAt = LocalDateTime.now())
        val saved = platformRepository.save(platform)
        log.infoEvent("crud.delete", "entity" to "platform", "entityId" to saved.id, "entityToken" to saved.platformToken, "name" to saved.name)
    }

    fun getPlatform(id: Long): Platform {
        return platformRepository.findById(id)
            .orElseThrow { ResourceNotFoundApiException("Platform not found with id: $id") }
    }

    fun getParticipants(id: Long): PlatformParticipantsResponse {
        val platform = getPlatform(id)
        val associations = subscriberPlatformRepository.findActiveByPlatformIdWithSubscriber(id)
        val participantsCount = associations.size

        val quote = if (platform.currency == Currency.BRL) null else exchangeRateService.getLatestBrlRate(platform.currency)
        val priceInBrl = when (platform.currency) {
            Currency.BRL -> platform.price.setScale(FINAL_SCALE, ROUNDING)
            else -> quote?.rateToBrl?.let { platform.price.multiply(it).setScale(FINAL_SCALE, ROUNDING) }
        }

        val individualShare = if (participantsCount == 0) {
            null
        } else {
            priceInBrl?.divide(BigDecimal(participantsCount), INTERMEDIATE_SCALE, ROUNDING)
                ?.setScale(FINAL_SCALE, ROUNDING)
        }
        val individualShareOriginal = if (platform.currency == Currency.BRL || participantsCount == 0) {
            null
        } else {
            platform.price.divide(BigDecimal(participantsCount), INTERMEDIATE_SCALE, ROUNDING)
                .setScale(FINAL_SCALE, ROUNDING)
        }

        val participants = associations.map { assoc ->
            PlatformParticipantItem(
                subscriberId = assoc.subscriber.id!!,
                subscriberName = assoc.subscriber.name,
                subscriberEmail = assoc.subscriber.email,
                subscribedAt = assoc.subscribedAt,
                individualShare = individualShare,
                individualShareOriginal = individualShareOriginal
            )
        }

        log.infoEvent("crud.get", "entity" to "platform-participants", "entityId" to platform.id, "entityToken" to platform.platformToken, "participantsCount" to participantsCount)

        return PlatformParticipantsResponse(
            platformId = platform.id!!,
            platformName = platform.name,
            price = platform.price,
            currency = platform.currency,
            priceInBrl = priceInBrl,
            participantsCount = participantsCount,
            individualShare = individualShare,
            individualShareOriginal = individualShareOriginal,
            participants = participants
        )
    }

    private fun toDto(platform: Platform, quote: ExchangeRateQuote?): PlatformResponse {
        val priceInBrl = when (platform.currency) {
            Currency.BRL -> platform.price.setScale(2, RoundingMode.HALF_UP)
            else -> quote?.rateToBrl
                ?.let { platform.price.multiply(it).setScale(2, RoundingMode.HALF_UP) }
        }

        return PlatformResponse(
            id = platform.id,
            name = platform.name,
            price = platform.price,
            currency = platform.currency,
            priceInBrl = priceInBrl,
            exchangeRateToBrl = if (platform.currency == Currency.BRL) BigDecimal.ONE else quote?.rateToBrl,
            exchangeRateDate = if (platform.currency == Currency.BRL) null else quote?.quotedAt?.toLocalDate(),
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
