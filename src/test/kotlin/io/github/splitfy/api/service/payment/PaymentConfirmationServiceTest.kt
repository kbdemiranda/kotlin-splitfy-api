package io.github.splitfy.api.service.payment

import io.github.splitfy.api.domain.entity.PaymentConfirmation
import io.github.splitfy.api.domain.entity.Platform
import io.github.splitfy.api.domain.entity.Subscriber
import io.github.splitfy.api.domain.entity.SubscriberPlatform
import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.domain.enums.Currency
import io.github.splitfy.api.domain.enums.PaymentConfirmationStatus
import io.github.splitfy.api.domain.enums.ServiceType
import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.repository.PaymentConfirmationRepository
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.repository.SubscriberRepository
import io.github.splitfy.api.web.paymnets.dto.PaymentConfirmationPlatformsRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.Optional
import java.util.UUID

class PaymentConfirmationServiceTest {

    private val subscriberRepository: SubscriberRepository = mock()
    private val subscriberPlatformRepository: SubscriberPlatformRepository = mock()
    private val paymentConfirmationRepository: PaymentConfirmationRepository = mock()

    private lateinit var service: PaymentConfirmationService

    @BeforeEach
    fun setup() {
        service = PaymentConfirmationService(
            subscriberRepository,
            subscriberPlatformRepository,
            paymentConfirmationRepository
        )
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `admin can confirm multiple platforms for a subscriber`() {
        setAuth("admin@splitfy.com", "ROLE_ADMIN")

        val subscriber1 = subscriber(id = 1L, email = "s1@example.com")
        val association11 = association(subscriber1, platform(11L, "Netflix"))
        val association12 = association(subscriber1, platform(12L, "Spotify"))

        whenever(subscriberRepository.findById(1L)).thenReturn(Optional.of(subscriber1))
        whenever(subscriberRepository.findByFinancialResponsibleSubscriberIdAndDeletedAtIsNull(1L)).thenReturn(emptyList())
        whenever(subscriberPlatformRepository.findBySubscriberIdAndPlatformId(1L, 11L)).thenReturn(association11)
        whenever(subscriberPlatformRepository.findBySubscriberIdAndPlatformId(1L, 12L)).thenReturn(association12)
        whenever(paymentConfirmationRepository.findBySubscriberIdAndPlatformIdAndReferenceMonthAndDeletedAtIsNull(any(), any(), any()))
            .thenReturn(null)
        whenever(paymentConfirmationRepository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as PaymentConfirmation).copy(id = 100L)
        }

        val response = service.createAdminConfirmations(
            subscriberId = 1L,
            request = PaymentConfirmationPlatformsRequest(
                referenceMonth = "2026-01",
                platformIds = listOf(11L, 12L)
            )
        )

        assertEquals(2, response.size)
        response.forEach { assertEquals(PaymentConfirmationStatus.CONFIRMED, it.status) }
    }

    @Test
    fun `create admin confirmations throws when platform is already paid`() {
        setAuth("admin@splitfy.com", "ROLE_ADMIN")

        val ownerSubscriber = subscriber(id = 1L, email = "s1@example.com")
        val netflix = platform(11L, "Netflix")
        val association = association(ownerSubscriber, netflix)

        whenever(subscriberRepository.findById(1L)).thenReturn(Optional.of(subscriber(id = 1L, email = "s1@example.com")))
        whenever(subscriberRepository.findByFinancialResponsibleSubscriberIdAndDeletedAtIsNull(1L)).thenReturn(emptyList())
        whenever(subscriberPlatformRepository.findBySubscriberIdAndPlatformId(1L, 11L)).thenReturn(association)
        whenever(paymentConfirmationRepository.findBySubscriberIdAndPlatformIdAndReferenceMonthAndDeletedAtIsNull(any(), any(), any()))
            .thenReturn(
                PaymentConfirmation(
                    id = 10L,
                    subscriber = ownerSubscriber,
                    platform = netflix,
                    referenceMonth = YearMonth.now(),
                    status = PaymentConfirmationStatus.CONFIRMED,
                    requestedByEmail = "admin@splitfy.com",
                    requestedAt = LocalDateTime.now()
                )
            )

        assertThrows(BadRequestApiException::class.java) {
            service.createAdminConfirmations(
                subscriberId = 1L,
                request = PaymentConfirmationPlatformsRequest(
                    referenceMonth = "2026-01",
                    platformIds = listOf(11L)
                )
            )
        }
    }

    @Test
    fun `create admin confirmations throws when platform ids are empty`() {
        setAuth("admin@splitfy.com", "ROLE_ADMIN")

        assertThrows(BadRequestApiException::class.java) {
            service.createAdminConfirmations(
                subscriberId = 1L,
                request = PaymentConfirmationPlatformsRequest(
                    referenceMonth = "2026-01",
                    platformIds = emptyList()
                )
            )
        }
    }

    @Test
    fun `create admin confirmations throws when reference month is invalid`() {
        setAuth("admin@splitfy.com", "ROLE_ADMIN")

        assertThrows(BadRequestApiException::class.java) {
            service.createAdminConfirmations(
                subscriberId = 1L,
                request = PaymentConfirmationPlatformsRequest(
                    referenceMonth = "2026/01",
                    platformIds = listOf(11L)
                )
            )
        }
    }

    @Test
    fun `admin confirmation for payer also confirms covered subscribers`() {
        setAuth("admin@splitfy.com", "ROLE_ADMIN")

        val payer = subscriber(id = 6L, email = "payer@example.com")
        val child2 = subscriber(id = 2L, email = "child2@example.com").copy(financialResponsibleSubscriber = payer)
        val child4 = subscriber(id = 4L, email = "child4@example.com").copy(financialResponsibleSubscriber = payer)
        val netflix = platform(11L, "Netflix")

        val associationPayer = association(payer, netflix)
        val associationChild2 = association(child2, netflix)
        val associationChild4 = association(child4, netflix)

        whenever(subscriberRepository.findById(6L)).thenReturn(Optional.of(payer))
        whenever(subscriberRepository.findByFinancialResponsibleSubscriberIdAndDeletedAtIsNull(6L)).thenReturn(listOf(child2, child4))
        whenever(subscriberPlatformRepository.findBySubscriberIdAndPlatformId(6L, 11L)).thenReturn(associationPayer)
        whenever(subscriberPlatformRepository.findBySubscriberIdAndPlatformId(2L, 11L)).thenReturn(associationChild2)
        whenever(subscriberPlatformRepository.findBySubscriberIdAndPlatformId(4L, 11L)).thenReturn(associationChild4)
        whenever(paymentConfirmationRepository.findBySubscriberIdAndPlatformIdAndReferenceMonthAndDeletedAtIsNull(any(), any(), any()))
            .thenReturn(null)
        whenever(paymentConfirmationRepository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as PaymentConfirmation).copy(id = 200L + (invocation.arguments[0] as PaymentConfirmation).subscriber.id!!)
        }

        val response = service.createAdminConfirmations(
            subscriberId = 6L,
            request = PaymentConfirmationPlatformsRequest(
                referenceMonth = "2026-03",
                platformIds = listOf(11L)
            )
        )

        assertEquals(3, response.size)
        assertEquals(setOf(6L, 2L, 4L), response.map { it.subscriberId }.toSet())
        response.forEach { assertEquals(PaymentConfirmationStatus.CONFIRMED, it.status) }
    }

    @Test
    fun `admin can list pending confirmations with semantic data`() {
        setAuth("admin@splitfy.com", "ROLE_ADMIN")

        val subscriber = subscriber(id = 1L, email = "owner@example.com")
        val platform = platform(11L, "Netflix")
        val pending = PaymentConfirmation(
            id = 77L,
            subscriber = subscriber,
            platform = platform,
            referenceMonth = YearMonth.of(2026, 2),
            status = PaymentConfirmationStatus.PENDING,
            requestedByEmail = "owner@example.com",
            requestedAt = LocalDateTime.now()
        )

        whenever(paymentConfirmationRepository.findAllPendingWithDetails(PaymentConfirmationStatus.PENDING))
            .thenReturn(listOf(pending))

        val result = service.listPendingConfirmations(null)

        assertEquals(1, result.size)
        val row = result.first()
        assertEquals(77L, row.confirmationId)
        assertEquals("Subscriber 1", row.subscriber.name)
        assertEquals("owner@example.com", row.subscriber.email)
        assertEquals("Netflix", row.platform.name)
        assertEquals(Currency.BRL, row.platform.currency)
        assertTrue(row.platform.price > java.math.BigDecimal.ZERO)
    }

    @Test
    fun `list pending can be filtered by reference month`() {
        setAuth("admin@splitfy.com", "ROLE_ADMIN")
        val refMonth = YearMonth.of(2026, 2)
        whenever(
            paymentConfirmationRepository.findAllPendingWithDetailsByReferenceMonth(
                PaymentConfirmationStatus.PENDING,
                refMonth
            )
        ).thenReturn(emptyList())

        val result = service.listPendingConfirmations("2026-02")

        assertTrue(result.isEmpty())
    }

    private fun setAuth(email: String, role: String) {
        val auth = UsernamePasswordAuthenticationToken(
            email,
            "",
            listOf(SimpleGrantedAuthority(role))
        )
        SecurityContextHolder.getContext().authentication = auth
    }

    private fun subscriber(id: Long, email: String): Subscriber {
        return Subscriber(
            id = id,
            subscriberToken = UUID.randomUUID(),
            name = "Subscriber $id",
            email = email,
            createdAt = LocalDateTime.now()
        )
    }

    private fun platform(id: Long, name: String): Platform {
        return Platform(
            id = id,
            platformToken = UUID.randomUUID(),
            name = name,
            price = java.math.BigDecimal("10.00"),
            currency = Currency.BRL,
            serviceType = ServiceType.SOFTWARE,
            totalSlots = 5,
            availableSlots = 3,
            createdAt = LocalDateTime.now(),
            billingCycle = BillingCycle.MONTHLY
        )
    }

    private fun association(subscriber: Subscriber, platform: Platform): SubscriberPlatform {
        return SubscriberPlatform(
            id = 1L,
            subscriber = subscriber,
            platform = platform,
            subscribedAt = LocalDateTime.now(),
            isActive = true,
            createdAt = LocalDateTime.now()
        )
    }
}
