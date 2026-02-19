package io.github.splitfy.api.service.payment

import io.github.splitfy.api.domain.entity.PaymentConfirmation
import io.github.splitfy.api.domain.entity.Platform
import io.github.splitfy.api.domain.entity.Profile
import io.github.splitfy.api.domain.entity.Subscriber
import io.github.splitfy.api.domain.entity.SubscriberPlatform
import io.github.splitfy.api.domain.entity.User
import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.domain.enums.Currency
import io.github.splitfy.api.domain.enums.PaymentConfirmationStatus
import io.github.splitfy.api.domain.enums.ProfileName
import io.github.splitfy.api.domain.enums.ServiceType
import io.github.splitfy.api.exception.ResourceNotFoundApiException
import io.github.splitfy.api.repository.PaymentConfirmationRepository
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.repository.SubscriberRepository
import io.github.splitfy.api.repository.UserRepository
import io.github.splitfy.api.service.email.EmailService
import io.github.splitfy.api.service.email.EmailTemplateService
import io.github.splitfy.api.web.paymnets.dto.PaymentConfirmationBatchRequest
import io.github.splitfy.api.web.paymnets.dto.PaymentConfirmationItemRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.access.AccessDeniedException
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
    private val userRepository: UserRepository = mock()
    private val emailService: EmailService = mock()
    private val emailTemplateService = EmailTemplateService()

    private lateinit var service: PaymentConfirmationService

    @BeforeEach
    fun setup() {
        service = PaymentConfirmationService(
            subscriberRepository,
            subscriberPlatformRepository,
            paymentConfirmationRepository,
            userRepository,
            emailService,
            emailTemplateService
        )
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `admin can confirm payments for multiple subscribers and services`() {
        setAuth("admin@splitfy.com", "ROLE_ADMIN")

        val subscriber1 = subscriber(id = 1L, email = "s1@example.com")
        val subscriber2 = subscriber(id = 2L, email = "s2@example.com")
        val association11 = association(subscriber1, platform(11L, "Netflix"))
        val association12 = association(subscriber1, platform(12L, "Spotify"))
        val association21 = association(subscriber2, platform(21L, "Disney"))

        whenever(subscriberRepository.findById(1L)).thenReturn(Optional.of(subscriber1))
        whenever(subscriberRepository.findById(2L)).thenReturn(Optional.of(subscriber2))
        whenever(subscriberPlatformRepository.findBySubscriberIdAndPlatformId(1L, 11L)).thenReturn(association11)
        whenever(subscriberPlatformRepository.findBySubscriberIdAndPlatformId(1L, 12L)).thenReturn(association12)
        whenever(subscriberPlatformRepository.findBySubscriberIdAndPlatformId(2L, 21L)).thenReturn(association21)
        whenever(paymentConfirmationRepository.findBySubscriberIdAndPlatformIdAndReferenceMonthAndDeletedAtIsNull(any(), any(), any()))
            .thenReturn(null)
        whenever(paymentConfirmationRepository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as PaymentConfirmation).copy(id = 100L)
        }

        val response = service.createConfirmations(
            PaymentConfirmationBatchRequest(
                referenceMonth = "2026-02",
                confirmations = listOf(
                    PaymentConfirmationItemRequest(subscriberId = 1L, platformIds = listOf(11L, 12L)),
                    PaymentConfirmationItemRequest(subscriberId = 2L, platformIds = listOf(21L))
                )
            )
        )

        assertEquals(3, response.size)
        response.forEach { assertEquals(PaymentConfirmationStatus.CONFIRMED, it.status) }
        verify(emailService, never()).send(any(), any(), any())
        verify(emailService, never()).sendHtml(any(), any(), any())
    }

    @Test
    fun `viewer can request own payment confirmation and it becomes pending with admin notification`() {
        setAuth("owner@example.com", "ROLE_VIEWER")

        val ownerSubscriber = subscriber(id = 1L, email = "owner@example.com")
        val adminUser = adminUser()
        val association = association(ownerSubscriber, platform(11L, "Netflix"))
        val referenceMonth = YearMonth.of(2026, 2)

        whenever(subscriberRepository.findById(1L)).thenReturn(Optional.of(ownerSubscriber))
        whenever(subscriberPlatformRepository.findBySubscriberIdAndPlatformId(1L, 11L)).thenReturn(association)
        whenever(paymentConfirmationRepository.findBySubscriberIdAndPlatformIdAndReferenceMonthAndDeletedAtIsNull(1L, 11L, referenceMonth))
            .thenReturn(null)
        whenever(paymentConfirmationRepository.save(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as PaymentConfirmation).copy(id = 200L)
        }
        whenever(userRepository.findActiveEmailsByProfileName(ProfileName.ADMIN))
            .thenReturn(listOf(adminUser.email))

        val response = service.createConfirmations(
            PaymentConfirmationBatchRequest(
                referenceMonth = "2026-02",
                confirmations = listOf(PaymentConfirmationItemRequest(subscriberId = 1L, platformIds = listOf(11L)))
            )
        )

        assertEquals(1, response.size)
        assertEquals(PaymentConfirmationStatus.PENDING, response.first().status)
        verify(emailService, times(1)).sendHtml(eq("admin@splitfy.com"), any(), any())
    }

    @Test
    fun `viewer cannot confirm payments for another subscriber`() {
        setAuth("owner@example.com", "ROLE_VIEWER")

        whenever(subscriberRepository.findById(2L)).thenReturn(Optional.of(subscriber(id = 2L, email = "other@example.com")))

        assertThrows(AccessDeniedException::class.java) {
            service.createConfirmations(
                PaymentConfirmationBatchRequest(
                    referenceMonth = "2026-02",
                    confirmations = listOf(PaymentConfirmationItemRequest(subscriberId = 2L, platformIds = listOf(11L)))
                )
            )
        }
    }

    @Test
    fun `admin can approve pending confirmation`() {
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

        whenever(paymentConfirmationRepository.findByIdAndDeletedAtIsNull(77L)).thenReturn(pending)
        whenever(paymentConfirmationRepository.save(any())).thenAnswer { invocation -> invocation.arguments[0] as PaymentConfirmation }

        val approved = service.approveConfirmation(77L)

        assertEquals(PaymentConfirmationStatus.CONFIRMED, approved.status)
        assertEquals("admin@splitfy.com", approved.validatedByEmail)
    }

    @Test
    fun `approve throws when confirmation does not exist`() {
        setAuth("admin@splitfy.com", "ROLE_ADMIN")
        whenever(paymentConfirmationRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(null)

        assertThrows(ResourceNotFoundApiException::class.java) {
            service.approveConfirmation(999L)
        }
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

    private fun adminUser(): User {
        return User(
            id = UUID.randomUUID(),
            name = "Admin",
            email = "admin@splitfy.com",
            password = "x",
            profile = Profile(name = ProfileName.ADMIN),
            isEnabled = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }
}
