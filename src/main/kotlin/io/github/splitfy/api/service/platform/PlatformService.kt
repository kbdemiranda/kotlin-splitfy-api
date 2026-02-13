package io.github.splitfy.api.service.platform

import io.github.splitfy.api.domain.entity.Platform
import io.github.splitfy.api.domain.enums.Currency
import io.github.splitfy.api.exception.ResourceNotFoundApiException
import io.github.splitfy.api.repository.PlatformRepository
import io.github.splitfy.api.service.exchange.ExchangeRateQuote
import io.github.splitfy.api.service.exchange.ExchangeRateService
import io.github.splitfy.api.web.platform.dto.PlatformRequest
import io.github.splitfy.api.web.platform.dto.PlatformResponse
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
    private val exchangeRateService: ExchangeRateService
) {

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
        return platforms.map { toDto(it, ratesByCurrency[it.currency]) }
    }

    fun findById(id: Long): PlatformResponse? {
        val platform = getPlatform(id)
        val quote = if (platform.currency == Currency.BRL) null else exchangeRateService.getLatestBrlRate(platform.currency)
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
        return toDto(saved, null)
    }

    fun delete(id: Long) {
        val platform = getPlatform(id).copy(deletedAt = LocalDateTime.now())
        platformRepository.save(platform)
    }

    fun getPlatform(id: Long): Platform {
        return platformRepository.findById(id)
            .orElseThrow { ResourceNotFoundApiException("Platform not found with id: $id") }
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
