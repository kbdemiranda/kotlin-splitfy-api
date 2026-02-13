package io.github.splitfy.api.service.platform

import io.github.splitfy.api.repository.PlatformRepository
import io.github.splitfy.api.web.platform.dto.PlatformRequest
import io.github.splitfy.api.domain.entity.Platform
import io.github.splitfy.api.domain.enums.ServiceType
import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.domain.enums.Currency
import io.github.splitfy.api.exception.ResourceNotFoundApiException
import io.github.splitfy.api.service.exchange.ExchangeRateQuote
import io.github.splitfy.api.service.exchange.ExchangeRateService
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class PlatformServiceTest {

    private val platformRepository: PlatformRepository = mock()
    private val exchangeRateService: ExchangeRateService = mock()
    private val service = PlatformService(platformRepository, exchangeRateService)

    @Test
    fun `create saves and returns dto`() {
        val req = PlatformRequest(
            name = "Netflix",
            price = BigDecimal("29.90"),
            currency = Currency.USD,
            url = "https://netflix.com",
            serviceType = ServiceType.STREAMING_VIDEO,
            totalSlots = 4,
            availableSlots = 4,
            billingCycle = BillingCycle.MONTHLY,
            billingDay = null
        )

        whenever(platformRepository.save(any())).thenAnswer { invocation -> invocation.getArgument(0) as Platform }

        val resp = service.create(req)

        assertEquals(req.name, resp.name)
        assertEquals(req.price, resp.price)
        assertEquals(req.currency, resp.currency)
        assertEquals(null, resp.priceInBrl)
        assertNotNull(resp.createdAt)
        // verify repository save called
        verify(platformRepository).save(any())
    }

    @Test
    fun `getPlatform not found throws ResourceNotFoundApiException`() {
        whenever(platformRepository.findById(1L)).thenReturn(Optional.empty())

        assertFailsWith<ResourceNotFoundApiException> {
            service.getPlatform(1L)
        }
    }

    @Test
    fun `update preserves token and updates fields`() {
        val existing = Platform(
            id = 1L,
            platformToken = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            name = "Old",
            price = BigDecimal("10.00"),
            currency = Currency.BRL,
            url = null,
            serviceType = ServiceType.STREAMING_VIDEO,
            totalSlots = 2,
            availableSlots = 2,
            createdAt = LocalDateTime.now(),
            updatedAt = null,
            deletedAt = null,
            billingCycle = BillingCycle.MONTHLY,
            billingDate = null
        )

        val req = PlatformRequest(
            name = "New",
            price = BigDecimal("20.00"),
            currency = Currency.EUR,
            url = "https://new",
            serviceType = ServiceType.STREAMING_VIDEO,
            totalSlots = 3,
            availableSlots = 3,
            billingCycle = BillingCycle.MONTHLY,
            billingDay = null
        )

        whenever(platformRepository.findById(1L)).thenReturn(Optional.of(existing))
        whenever(platformRepository.save(any())).thenAnswer { invocation -> invocation.getArgument(0) as Platform }

        service.update(1L, req)

        // capture saved entity and ensure token preserved
        val captor = argumentCaptor<Platform>()
        verify(platformRepository).save(captor.capture())
        assertEquals(existing.platformToken, captor.firstValue.platformToken)
        assertEquals(Currency.EUR, captor.firstValue.currency)
    }

    @Test
    fun `findById converts foreign currency price to BRL using latest quote`() {
        val existing = Platform(
            id = 2L,
            platformToken = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            name = "Foreign Service",
            price = BigDecimal("10.00"),
            currency = Currency.USD,
            url = null,
            serviceType = ServiceType.SOFTWARE,
            totalSlots = 1,
            availableSlots = 1,
            createdAt = LocalDateTime.now(),
            updatedAt = null,
            deletedAt = null,
            billingCycle = BillingCycle.MONTHLY,
            billingDate = null
        )
        val quoteAt = LocalDateTime.of(2026, 2, 12, 13, 4, 38)

        whenever(platformRepository.findById(2L)).thenReturn(Optional.of(existing))
        whenever(exchangeRateService.getLatestBrlRate(Currency.USD)).thenReturn(
            ExchangeRateQuote(
                currency = Currency.USD,
                rateToBrl = BigDecimal("5.1674"),
                quotedAt = quoteAt
            )
        )

        val response = service.findById(2L)!!

        assertEquals(BigDecimal("51.67"), response.priceInBrl)
        assertEquals(BigDecimal("5.1674"), response.exchangeRateToBrl)
        assertEquals(quoteAt.toLocalDate(), response.exchangeRateDate)
    }
}
