package io.github.splitfy.api.service.platform

import io.github.splitfy.api.domain.enums.PaymentConfirmationStatus
import io.github.splitfy.api.repository.PaymentConfirmationRepository
import io.github.splitfy.api.repository.PlatformRepository
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.web.platform.dto.PlatformRequest
import io.github.splitfy.api.domain.entity.Platform
import io.github.splitfy.api.domain.entity.PaymentConfirmation
import io.github.splitfy.api.domain.entity.Subscriber
import io.github.splitfy.api.domain.entity.SubscriberPlatform
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
import java.time.YearMonth
import java.util.Optional
import java.util.UUID
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import kotlin.test.assertNull

class PlatformServiceTest {

    private val platformRepository: PlatformRepository = mock()
    private val exchangeRateService: ExchangeRateService = mock()
    private val subscriberPlatformRepository: SubscriberPlatformRepository = mock()
    private val paymentConfirmationRepository: PaymentConfirmationRepository = mock()
    private val service = PlatformService(platformRepository, exchangeRateService, subscriberPlatformRepository, paymentConfirmationRepository)

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

    @Test
    fun `findAll without filter returns BRL platforms without exchange lookup`() {
        val pageable = PageRequest.of(0, 10)
        val platform = platform(
            id = 3L,
            name = "Brazilian Service",
            price = BigDecimal("12.345"),
            currency = Currency.BRL
        )
        whenever(platformRepository.findByDeletedAtIsNull(pageable))
            .thenReturn(PageImpl(listOf(platform), pageable, 1))

        val response = service.findAll(pageable, null)

        assertEquals(1, response.totalElements)
        assertEquals(BigDecimal("12.35"), response.content.first().priceInBrl)
        assertEquals(BigDecimal.ONE, response.content.first().exchangeRateToBrl)
        assertNull(response.content.first().exchangeRateDate)
        verify(exchangeRateService, never()).getLatestBrlRate(any())
    }

    @Test
    fun `findAll with name filter converts foreign currency platforms`() {
        val pageable = PageRequest.of(0, 10)
        val quoteAt = LocalDateTime.of(2026, 4, 20, 12, 0)
        val platform = platform(
            id = 4L,
            name = "Dollar Service",
            price = BigDecimal("10.00"),
            currency = Currency.USD
        )
        whenever(platformRepository.findByDeletedAtIsNullAndNameContainingIgnoreCase("Dollar", pageable))
            .thenReturn(PageImpl(listOf(platform), pageable, 1))
        whenever(exchangeRateService.getLatestBrlRate(Currency.USD)).thenReturn(
            ExchangeRateQuote(Currency.USD, BigDecimal("5.123"), quoteAt)
        )

        val response = service.findAll(pageable, "Dollar")

        assertEquals(BigDecimal("51.23"), response.content.first().priceInBrl)
        assertEquals(BigDecimal("5.123"), response.content.first().exchangeRateToBrl)
        assertEquals(quoteAt.toLocalDate(), response.content.first().exchangeRateDate)
        verify(platformRepository).findByDeletedAtIsNullAndNameContainingIgnoreCase("Dollar", pageable)
    }

    @Test
    fun `delete soft deletes existing platform`() {
        val existing = platform(5L, "Old Service", BigDecimal("20.00"), Currency.BRL)
        whenever(platformRepository.findById(5L)).thenReturn(Optional.of(existing))
        whenever(platformRepository.save(any())).thenAnswer { invocation -> invocation.getArgument(0) }

        service.delete(5L)

        val captor = argumentCaptor<Platform>()
        verify(platformRepository).save(captor.capture())
        assertEquals(existing.id, captor.firstValue.id)
        assertNotNull(captor.firstValue.deletedAt)
    }

    @Test
    fun `findById returns BRL conversion without exchange quote`() {
        val existing = platform(6L, "Local Service", BigDecimal("30.00"), Currency.BRL)
        whenever(platformRepository.findById(6L)).thenReturn(Optional.of(existing))

        val response = service.findById(6L)!!

        assertEquals(BigDecimal("30.00"), response.priceInBrl)
        assertEquals(BigDecimal.ONE, response.exchangeRateToBrl)
        assertNull(response.exchangeRateDate)
        verify(exchangeRateService, never()).getLatestBrlRate(any())
    }

    @Test
    fun `getParticipants splits BRL price equally among active participants`() {
        val existing = platform(7L, "Local Service", BigDecimal("30.00"), Currency.BRL)
        val subscriberA = subscriber(1L, "Ana", "ana@example.com")
        val subscriberB = subscriber(2L, "Bruno", "bruno@example.com")
        val subscribedAt = LocalDateTime.of(2026, 1, 10, 9, 0)

        whenever(platformRepository.findById(7L)).thenReturn(Optional.of(existing))
        whenever(subscriberPlatformRepository.findActiveByPlatformIdWithSubscriber(7L)).thenReturn(
            listOf(
                subscriberPlatform(subscriberA, existing, subscribedAt),
                subscriberPlatform(subscriberB, existing, subscribedAt)
            )
        )

        val response = service.getParticipants(7L)

        assertEquals(2, response.participantsCount)
        assertEquals(BigDecimal("30.00"), response.priceInBrl)
        assertEquals(BigDecimal("15.00"), response.individualShare)
        assertNull(response.individualShareOriginal)
        assertEquals(listOf(1L, 2L), response.participants.map { it.subscriberId })
        assertEquals(BigDecimal("15.00"), response.participants[0].individualShare)
        assertEquals(0, response.participants[0].paidCyclesCount)
        assertEquals(BigDecimal("0.00"), response.participants[0].totalPaid)
        assertEquals(BigDecimal("0.00"), response.participants[0].totalIfSubscribedAlone)
        verify(exchangeRateService, never()).getLatestBrlRate(any())
    }

    @Test
    fun `getParticipants sums total paid and total if subscribed alone from confirmed payments`() {
        val existing = platform(11L, "Foreign Service", BigDecimal("30.00"), Currency.USD)
        val subscriberA = subscriber(4L, "Dara", "dara@example.com")
        val subscriberB = subscriber(5L, "Elis", "elis@example.com")
        val subscribedAt = LocalDateTime.of(2026, 1, 10, 9, 0)

        whenever(platformRepository.findById(11L)).thenReturn(Optional.of(existing))
        whenever(exchangeRateService.getLatestBrlRate(Currency.USD)).thenReturn(
            ExchangeRateQuote(Currency.USD, BigDecimal("5.00"), LocalDateTime.now())
        )
        whenever(subscriberPlatformRepository.findActiveByPlatformIdWithSubscriber(11L)).thenReturn(
            listOf(
                subscriberPlatform(subscriberA, existing, subscribedAt),
                subscriberPlatform(subscriberB, existing, subscribedAt)
            )
        )
        whenever(
            paymentConfirmationRepository.findBySubscriberIdInAndPlatformIdAndStatusAndDeletedAtIsNull(
                listOf(4L, 5L), 11L, PaymentConfirmationStatus.CONFIRMED
            )
        ).thenReturn(
            listOf(
                paymentConfirmation(subscriberA, existing),
                paymentConfirmation(subscriberA, existing),
                paymentConfirmation(subscriberB, existing)
            )
        )

        val response = service.getParticipants(11L)

        // individualShare = 150.00 / 2 = 75.00 BRL; individualShareOriginal = 15.00 USD
        val dara = response.participants.first { it.subscriberId == 4L }
        val elis = response.participants.first { it.subscriberId == 5L }

        assertEquals(2, dara.paidCyclesCount)
        assertEquals(BigDecimal("150.00"), dara.totalPaid)
        assertEquals(BigDecimal("30.00"), dara.totalPaidOriginal)
        assertEquals(BigDecimal("300.00"), dara.totalIfSubscribedAlone)
        assertEquals(BigDecimal("60.00"), dara.totalIfSubscribedAloneOriginal)

        assertEquals(1, elis.paidCyclesCount)
        assertEquals(BigDecimal("75.00"), elis.totalPaid)
        assertEquals(BigDecimal("15.00"), elis.totalPaidOriginal)
        assertEquals(BigDecimal("150.00"), elis.totalIfSubscribedAlone)
        assertEquals(BigDecimal("30.00"), elis.totalIfSubscribedAloneOriginal)
    }

    @Test
    fun `getParticipants converts foreign currency price before splitting`() {
        val existing = platform(8L, "Foreign Service", BigDecimal("30.00"), Currency.USD)
        val subscriberA = subscriber(3L, "Carla", "carla@example.com")
        val subscribedAt = LocalDateTime.of(2026, 2, 1, 8, 0)

        whenever(platformRepository.findById(8L)).thenReturn(Optional.of(existing))
        whenever(exchangeRateService.getLatestBrlRate(Currency.USD)).thenReturn(
            ExchangeRateQuote(Currency.USD, BigDecimal("5.00"), LocalDateTime.now())
        )
        whenever(subscriberPlatformRepository.findActiveByPlatformIdWithSubscriber(8L)).thenReturn(
            listOf(subscriberPlatform(subscriberA, existing, subscribedAt))
        )

        val response = service.getParticipants(8L)

        assertEquals(1, response.participantsCount)
        assertEquals(BigDecimal("150.00"), response.priceInBrl)
        assertEquals(BigDecimal("150.00"), response.individualShare)
        assertEquals(BigDecimal("30.00"), response.individualShareOriginal)
        assertEquals(BigDecimal("30.00"), response.participants.first().individualShareOriginal)
    }

    @Test
    fun `getParticipants with no active participants returns null shares`() {
        val existing = platform(9L, "Empty Service", BigDecimal("20.00"), Currency.BRL)
        whenever(platformRepository.findById(9L)).thenReturn(Optional.of(existing))
        whenever(subscriberPlatformRepository.findActiveByPlatformIdWithSubscriber(9L)).thenReturn(emptyList())

        val response = service.getParticipants(9L)

        assertEquals(0, response.participantsCount)
        assertNull(response.individualShare)
        assertNull(response.individualShareOriginal)
        assertEquals(emptyList(), response.participants)
    }

    @Test
    fun `getParticipants not found throws ResourceNotFoundApiException`() {
        whenever(platformRepository.findById(10L)).thenReturn(Optional.empty())

        assertFailsWith<ResourceNotFoundApiException> {
            service.getParticipants(10L)
        }
    }

    private fun subscriber(id: Long, name: String, email: String): Subscriber {
        return Subscriber(
            id = id,
            subscriberToken = UUID.randomUUID(),
            name = name,
            email = email,
            financialResponsibleSubscriber = null,
            createdAt = LocalDateTime.now()
        )
    }

    private var nextReferenceMonth = YearMonth.of(2026, 1)

    private fun paymentConfirmation(subscriber: Subscriber, platform: Platform): PaymentConfirmation {
        val referenceMonth = nextReferenceMonth
        nextReferenceMonth = nextReferenceMonth.plusMonths(1)
        return PaymentConfirmation(
            id = null,
            subscriber = subscriber,
            platform = platform,
            referenceMonth = referenceMonth,
            status = PaymentConfirmationStatus.CONFIRMED,
            requestedByEmail = subscriber.email,
            requestedAt = LocalDateTime.now()
        )
    }

    private fun subscriberPlatform(subscriber: Subscriber, platform: Platform, subscribedAt: LocalDateTime): SubscriberPlatform {
        return SubscriberPlatform(
            id = null,
            subscriber = subscriber,
            platform = platform,
            subscribedAt = subscribedAt,
            unsubscribedAt = null,
            isActive = true
        )
    }

    private fun platform(
        id: Long,
        name: String,
        price: BigDecimal,
        currency: Currency,
    ): Platform {
        return Platform(
            id = id,
            platformToken = UUID.randomUUID(),
            name = name,
            price = price,
            currency = currency,
            url = null,
            serviceType = ServiceType.SOFTWARE,
            totalSlots = 3,
            availableSlots = 2,
            createdAt = LocalDateTime.now(),
            updatedAt = null,
            deletedAt = null,
            billingCycle = BillingCycle.MONTHLY,
            billingDate = null
        )
    }
}
