package io.github.splitfy.api.service.subscriber

import io.github.splitfy.api.repository.SubscriberRepository
import io.github.splitfy.api.repository.PlatformRepository
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.domain.entity.Subscriber
import io.github.splitfy.api.domain.entity.Platform
import io.github.splitfy.api.domain.entity.SubscriberPlatform
import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.exception.ConflictApiException
import io.github.splitfy.api.exception.ResourceNotFoundApiException
import io.github.splitfy.api.service.billing.BillingService
import io.github.splitfy.api.service.email.EmailService
import io.github.splitfy.api.service.email.EmailTemplateService
import io.github.splitfy.api.web.billing.dto.BillingItemDto
import io.github.splitfy.api.web.billing.dto.BillingResponse
import io.github.splitfy.api.web.billing.dto.Currency
import io.github.splitfy.api.web.subscriber.dto.PlatformAssociationRequest
import io.github.splitfy.api.web.billing.dto.SubscriberBillingEmailRequest
import org.mockito.kotlin.*
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull
import java.time.LocalDateTime
import java.time.YearMonth
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class SubscriberServiceTest {

    private val subscriberRepository: SubscriberRepository = mock()
    private val platformRepository: PlatformRepository = mock()
    private val subscriberPlatformRepository: SubscriberPlatformRepository = mock()
    private val billingService: BillingService = mock()
    private val emailService: EmailService = mock()
    private val emailTemplateService = EmailTemplateService()

    private val service = SubscriberService(
        subscriberRepository,
        platformRepository,
        subscriberPlatformRepository,
        billingService,
        emailService,
        emailTemplateService,
        "123.456.789-00",
        "000201010212PIX-TEST"
    )

    private fun sampleSubscriber(): Subscriber {
        return Subscriber(
            id = 1L,
            subscriberToken = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            name = "User",
            email = "u@example.com",
            financialResponsibleSubscriber = null,
            createdAt = LocalDateTime.now(),
            updatedAt = null,
            deletedAt = null
        )
    }

    @Test
    fun `create defaults financial responsible to self`() {
        whenever(subscriberRepository.save(any())).thenAnswer { invocation ->
            val subscriber = invocation.arguments[0] as Subscriber
            if (subscriber.id == null) subscriber.copy(id = 1L) else subscriber
        }

        val response = service.create(
            io.github.splitfy.api.web.subscriber.dto.SubscriberRequest(
                name = "User",
                email = "u@example.com"
            )
        )

        assertEquals(1L, response.id)
        assertEquals(1L, response.financialResponsibleSubscriberId)
        assertEquals("User", response.financialResponsibleSubscriberName)
    }

    @Test
    fun `create accepts explicit financial responsible`() {
        val responsible = sampleSubscriber().copy(id = 2L, name = "Holder", email = "holder@example.com")
        whenever(subscriberRepository.save(any())).thenAnswer { invocation ->
            val subscriber = invocation.arguments[0] as Subscriber
            if (subscriber.id == null) subscriber.copy(id = 1L) else subscriber
        }
        whenever(subscriberRepository.findById(2L)).thenReturn(Optional.of(responsible))

        val response = service.create(
            io.github.splitfy.api.web.subscriber.dto.SubscriberRequest(
                name = "User",
                email = "u@example.com",
                financialResponsibleSubscriberId = 2L
            )
        )

        assertEquals(2L, response.financialResponsibleSubscriberId)
        assertEquals("Holder", response.financialResponsibleSubscriberName)
    }

    @Test
    fun `update accepts explicit financial responsible`() {
        val current = sampleSubscriber()
        val responsible = sampleSubscriber().copy(id = 2L, name = "Holder", email = "holder@example.com")
        whenever(subscriberRepository.findById(1L)).thenReturn(Optional.of(current))
        whenever(subscriberRepository.findById(2L)).thenReturn(Optional.of(responsible))
        whenever(subscriberRepository.save(any())).thenAnswer { invocation -> invocation.arguments[0] as Subscriber }

        val response = service.update(
            1L,
            io.github.splitfy.api.web.subscriber.dto.SubscriberRequest(
                name = "User",
                email = "u@example.com",
                financialResponsibleSubscriberId = 2L
            )
        )

        assertEquals(2L, response?.financialResponsibleSubscriberId)
        assertEquals("Holder", response?.financialResponsibleSubscriberName)
    }

    private fun samplePlatform(availableSlots: Int = 5, deleted: Boolean = false): Platform {
        return samplePlatformWithId(2L, availableSlots, deleted)
    }

    // New helper to create platforms with different ids
    private fun samplePlatformWithId(id: Long, availableSlots: Int = 5, deleted: Boolean = false): Platform {
        return Platform(
            id = id,
            platformToken = UUID.fromString("00000000-0000-0000-0000-00000000000${(id % 10)}"),
            name = "P$id",
            price = BigDecimal.ONE,
            url = null,
            serviceType = io.github.splitfy.api.domain.enums.ServiceType.STREAMING_VIDEO,
            totalSlots = 5,
            availableSlots = availableSlots,
            createdAt = LocalDateTime.now(),
            updatedAt = null,
            deletedAt = if (deleted) LocalDateTime.now() else null,
            billingCycle = io.github.splitfy.api.domain.enums.BillingCycle.MONTHLY,
            billingDate = null
        )
    }

    @Test
    fun `associatePlatforms success creates association and decrements slots`() {
        val subscriber = sampleSubscriber()
        val platform = samplePlatform(availableSlots = 3)

        whenever(subscriberRepository.findById(1L)).thenReturn(Optional.of(subscriber))
        whenever(platformRepository.findById(2L)).thenReturn(Optional.of(platform))
        whenever(subscriberPlatformRepository.findBySubscriberIdAndPlatformId(1L, 2L)).thenReturn(null)
        whenever(subscriberPlatformRepository.save(any())).thenAnswer { it.getArgument(0) as SubscriberPlatform }
        whenever(platformRepository.save(any())).thenAnswer { it.getArgument(0) as Platform }

        val req = listOf(PlatformAssociationRequest(platformIds = listOf(2L)))

        service.associatePlatforms(1L, req)

        verify(subscriberPlatformRepository).save(any())
        verify(platformRepository).save(argThat { availableSlots == 2 })
    }

    @Test
    fun `associatePlatforms platform not found throws`() {
        whenever(subscriberRepository.findById(1L)).thenReturn(Optional.of(sampleSubscriber()))
        whenever(platformRepository.findById(99L)).thenReturn(Optional.empty())

        val req = listOf(PlatformAssociationRequest(platformIds = listOf(99L)))

        assertFailsWith<ResourceNotFoundApiException> {
            service.associatePlatforms(1L, req)
        }
    }

    @Test
    fun `associatePlatforms no available slots throws BadRequestApiException`() {
        whenever(subscriberRepository.findById(1L)).thenReturn(Optional.of(sampleSubscriber()))
        whenever(platformRepository.findById(2L)).thenReturn(Optional.of(samplePlatform(availableSlots = 0)))

        val req = listOf(PlatformAssociationRequest(platformIds = listOf(2L)))

        assertFailsWith<BadRequestApiException> {
            service.associatePlatforms(1L, req)
        }
    }

    @Test
    fun `associatePlatforms already associated throws ConflictApiException`() {
        val subscriber = sampleSubscriber()
        val platform = samplePlatform()
        val association = SubscriberPlatform(
            id = 10L,
            subscriber = subscriber,
            platform = platform,
            isActive = true,
            deletedAt = null,
            createdAt = LocalDateTime.now()
        )

        whenever(subscriberRepository.findById(1L)).thenReturn(Optional.of(sampleSubscriber()))
        whenever(platformRepository.findById(2L)).thenReturn(Optional.of(platform))
        whenever(subscriberPlatformRepository.findBySubscriberIdAndPlatformId(1L, 2L)).thenReturn(association)

        val req = listOf(PlatformAssociationRequest(platformIds = listOf(2L)))

        assertFailsWith<ConflictApiException> {
            service.associatePlatforms(1L, req)
        }
    }

    @Test
    fun `associatePlatforms with multiple platform ids processes all`() {
        val subscriber = sampleSubscriber()
        val platform2 = samplePlatformWithId(2L, availableSlots = 3)
        val platform3 = samplePlatformWithId(3L, availableSlots = 2)

        whenever(subscriberRepository.findById(1L)).thenReturn(Optional.of(subscriber))
        whenever(platformRepository.findById(2L)).thenReturn(Optional.of(platform2))
        whenever(platformRepository.findById(3L)).thenReturn(Optional.of(platform3))
        whenever(subscriberPlatformRepository.findBySubscriberIdAndPlatformId(1L, 2L)).thenReturn(null)
        whenever(subscriberPlatformRepository.findBySubscriberIdAndPlatformId(1L, 3L)).thenReturn(null)
        whenever(subscriberPlatformRepository.save(any())).thenAnswer { it.getArgument(0) as SubscriberPlatform }
        whenever(platformRepository.save(any())).thenAnswer { it.getArgument(0) as Platform }

        val req = listOf(PlatformAssociationRequest(platformIds = listOf(2L, 3L)))

        service.associatePlatforms(1L, req)

        // expect two saves for associations
        verify(subscriberPlatformRepository, times(2)).save(any())
        // expect platform 2 saved with availableSlots 2 and platform 3 saved with availableSlots 1
        verify(platformRepository).save(argThat { id == 2L && availableSlots == 2 })
        verify(platformRepository).save(argThat { id == 3L && availableSlots == 1 })
    }

    @Test
    fun `associatePlatforms with empty request does nothing`() {
        val req = emptyList<PlatformAssociationRequest>()

        service.associatePlatforms(1L, req)

        verifyNoInteractions(platformRepository, subscriberPlatformRepository)
        // subscriberRepository should also not be invoked
        verifyNoInteractions(subscriberRepository)
    }

    @Test
    fun `associatePlatforms reactivates inactive association`() {
        val subscriber = sampleSubscriber()
        val platform = samplePlatform(availableSlots = 3)
        val oldDeletedAt = LocalDateTime.now().minusDays(1)
        val association = SubscriberPlatform(
            id = 10L,
            subscriber = subscriber,
            platform = platform,
            isActive = false,
            deletedAt = oldDeletedAt,
            createdAt = LocalDateTime.now().minusDays(2)
        )

        whenever(subscriberRepository.findById(1L)).thenReturn(Optional.of(subscriber))
        whenever(platformRepository.findById(2L)).thenReturn(Optional.of(platform))
        whenever(subscriberPlatformRepository.findBySubscriberIdAndPlatformId(1L, 2L)).thenReturn(association)
        whenever(subscriberPlatformRepository.save(any())).thenAnswer { it.getArgument(0) as SubscriberPlatform }
        whenever(platformRepository.save(any())).thenAnswer { it.getArgument(0) as Platform }

        val req = listOf(PlatformAssociationRequest(platformIds = listOf(2L)))

        service.associatePlatforms(1L, req)

        verify(subscriberPlatformRepository).save(argThat {
            isActive && this.deletedAt == null && updatedAt != null
        })
        verify(platformRepository).save(argThat { availableSlots == 2 })
    }

    @Test
    fun `disassociatePlatforms success deactivates association and increments slots`() {
        val subscriber = sampleSubscriber()
        val platform = samplePlatform(availableSlots = 1)
        val association = SubscriberPlatform(
            id = 10L,
            subscriber = subscriber,
            platform = platform,
            createdAt = LocalDateTime.now(),
            updatedAt = null,
            unsubscribedAt = null,
            isActive = true
        )

        whenever(subscriberRepository.findById(1L)).thenReturn(Optional.of(subscriber))
        whenever(platformRepository.findById(2L)).thenReturn(Optional.of(platform))
        whenever(subscriberPlatformRepository.findBySubscriberIdAndPlatformId(1L, 2L)).thenReturn(association)
        whenever(subscriberPlatformRepository.save(any())).thenAnswer { it.getArgument(0) as SubscriberPlatform }
        whenever(platformRepository.save(any())).thenAnswer { it.getArgument(0) as Platform }

        val req = listOf(PlatformAssociationRequest(platformIds = listOf(2L)))

        service.disassociatePlatforms(1L, req)

        verify(subscriberPlatformRepository).save(argThat {
            isActive == false && deletedAt != null && updatedAt == null
        })
        verify(platformRepository).save(argThat { availableSlots == 2 })
    }

    @Test
    fun `disassociatePlatforms association missing throws BadRequestApiException`() {
        whenever(subscriberRepository.findById(1L)).thenReturn(Optional.of(sampleSubscriber()))
        whenever(platformRepository.findById(2L)).thenReturn(Optional.of(samplePlatform()))
        whenever(subscriberPlatformRepository.findBySubscriberIdAndPlatformId(1L, 2L)).thenReturn(null)

        val req = listOf(PlatformAssociationRequest(platformIds = listOf(2L)))

        assertFailsWith<BadRequestApiException> {
            service.disassociatePlatforms(1L, req)
        }
    }

    @Test
    fun `get returns subscriber with active subscriptions`() {
        val subscriber = sampleSubscriber()
        val platform = samplePlatformWithId(2L, availableSlots = 2)
        val association = SubscriberPlatform(
            id = 10L,
            subscriber = subscriber,
            platform = platform,
            subscribedAt = LocalDateTime.now().minusDays(2),
            isActive = true,
            deletedAt = null
        )

        whenever(subscriberRepository.findById(1L)).thenReturn(Optional.of(subscriber))
        whenever(subscriberPlatformRepository.findActiveBySubscriberIdWithPlatform(1L)).thenReturn(listOf(association))
        whenever(subscriberPlatformRepository.countActiveParticipantsByPlatformIds(listOf(2L))).thenReturn(
            listOf(
                object : SubscriberPlatformRepository.PlatformParticipantsCount {
                    override fun getPlatformId() = 2L
                    override fun getCount() = 2L
                }
            )
        )

        val response = service.get(1L)
        assertNotNull(response)
        val actual = checkNotNull(response)

        assertEquals(1L, actual.id)
        assertEquals("User", actual.name)
        assertEquals(1, actual.subscriptions.size)
        assertEquals(2L, actual.subscriptions.first().platformId)
        assertEquals("P2", actual.subscriptions.first().platformName)
    }

    @Test
    fun `sendBillingSummaryToEmails sends a single summary to all recipients`() {
        val subscriber1 = sampleSubscriber()
        val subscriber2 = sampleSubscriber().copy(id = 2L, name = "User 2", email = "u2@example.com")

        whenever(subscriberRepository.findAllById(listOf(1L, 2L))).thenReturn(listOf(subscriber1, subscriber2))
        whenever(billingService.getBillingForSubscriber(eq(1L), any())).thenReturn(
            BillingResponse(
                userId = 1L,
                name = subscriber1.name,
                email = subscriber1.email,
                referenceMonth = YearMonth.of(2026, 2),
                items = listOf(
                    BillingItemDto(
                        serviceId = 10L,
                        serviceName = "Netflix",
                        billingCycle = io.github.splitfy.api.domain.enums.BillingCycle.MONTHLY,
                        serviceMonthlyAmount = BigDecimal("55.90"),
                        participantsCount = 2,
                        userMonthlyShare = BigDecimal("27.95")
                    )
                ),
                totalMonthlyDue = BigDecimal("27.95"),
                currency = Currency.BRL
            )
        )
        whenever(billingService.getBillingForSubscriber(eq(2L), any())).thenReturn(
            BillingResponse(
                userId = 2L,
                name = subscriber2.name,
                email = subscriber2.email,
                referenceMonth = YearMonth.of(2026, 2),
                items = emptyList(),
                totalMonthlyDue = BigDecimal("0.00"),
                currency = Currency.BRL
            )
        )

        val request = SubscriberBillingEmailRequest(
            subscriberIds = listOf(1L, 2L),
            emails = listOf("finance@splitfy.com", "owner@splitfy.com")
        )

        service.sendBillingSummaryToEmails(request)

        verify(emailService).sendHtml(
            eq("finance@splitfy.com"),
            argThat { this.contains("Resumo de cobranças Splitfy") },
            argThat { this.contains("<html") },
            argThat { containsKey("pixQrCode") }
        )
        verify(emailService).sendHtml(
            eq("owner@splitfy.com"),
            argThat { this.contains("Resumo de cobranças Splitfy") },
            any(),
            argThat { containsKey("pixQrCode") }
        )
    }

    @Test
    fun `sendBillingSummaryToEmails includes responsible context for responsible recipient only`() {
        val responsible = sampleSubscriber().copy(name = "Kaique", email = "kaique@example.com")
        val dependent = sampleSubscriber().copy(
            id = 2L,
            name = "Ana",
            email = "ana@example.com",
            financialResponsibleSubscriber = responsible
        )
        val referenceMonth = YearMonth.of(2026, 4)

        whenever(subscriberRepository.findAllById(listOf(1L, 2L))).thenReturn(listOf(responsible, dependent))
        whenever(billingService.getBillingForSubscriber(eq(1L), any())).thenReturn(
            BillingResponse(
                userId = 1L,
                name = responsible.name,
                email = responsible.email,
                referenceMonth = referenceMonth,
                items = emptyList(),
                totalMonthlyDue = BigDecimal("10.00"),
                currency = Currency.BRL
            )
        )
        whenever(billingService.getBillingForSubscriber(eq(2L), any())).thenReturn(
            BillingResponse(
                userId = 2L,
                name = dependent.name,
                email = dependent.email,
                referenceMonth = referenceMonth,
                items = emptyList(),
                totalMonthlyDue = BigDecimal("20.00"),
                currency = Currency.BRL
            )
        )

        val request = SubscriberBillingEmailRequest(
            subscriberIds = listOf(1L, 2L),
            emails = listOf("kaique@example.com", "finance@splitfy.com"),
            referenceMonth = "2026-04"
        )

        service.sendBillingSummaryToEmails(request)

        val responsibleHtml = argumentCaptor<String>()
        verify(emailService).sendHtml(
            eq("kaique@example.com"),
            any(),
            responsibleHtml.capture(),
            argThat { containsKey("pixQrCode") }
        )
        assertTrue(responsibleHtml.firstValue.contains("Este resumo inclui cobranças de"))
        assertTrue(responsibleHtml.firstValue.contains("Kaique, Ana"))

        val financeHtml = argumentCaptor<String>()
        verify(emailService).sendHtml(
            eq("finance@splitfy.com"),
            any(),
            financeHtml.capture(),
            argThat { containsKey("pixQrCode") }
        )
        assertTrue(!financeHtml.firstValue.contains("Este resumo inclui cobranças de"))
    }

    @Test
    fun `sendBillingSummaryToEmails expands selected responsible subscriber to covered subscribers`() {
        val responsible = sampleSubscriber().copy(id = 1L, name = "Kaique", email = "kaique@example.com")
        val dependent1 = sampleSubscriber().copy(
            id = 2L,
            name = "Luci",
            email = "luci@example.com",
            financialResponsibleSubscriber = responsible
        )
        val dependent2 = sampleSubscriber().copy(
            id = 3L,
            name = "Tamires",
            email = "tamires@example.com",
            financialResponsibleSubscriber = responsible
        )
        val referenceMonth = YearMonth.of(2026, 4)

        whenever(subscriberRepository.findAllById(listOf(1L))).thenReturn(listOf(responsible))
        whenever(subscriberRepository.findByFinancialResponsibleSubscriberIdAndDeletedAtIsNull(1L)).thenReturn(
            listOf(responsible, dependent1, dependent2)
        )
        whenever(billingService.getBillingForSubscriber(eq(1L), any())).thenReturn(
            BillingResponse(
                userId = 1L,
                name = responsible.name,
                email = responsible.email,
                referenceMonth = referenceMonth,
                items = emptyList(),
                totalMonthlyDue = BigDecimal("94.62"),
                currency = Currency.BRL
            )
        )
        whenever(billingService.getBillingForSubscriber(eq(2L), any())).thenReturn(
            BillingResponse(
                userId = 2L,
                name = dependent1.name,
                email = dependent1.email,
                referenceMonth = referenceMonth,
                items = emptyList(),
                totalMonthlyDue = BigDecimal("13.12"),
                currency = Currency.BRL
            )
        )
        whenever(billingService.getBillingForSubscriber(eq(3L), any())).thenReturn(
            BillingResponse(
                userId = 3L,
                name = dependent2.name,
                email = dependent2.email,
                referenceMonth = referenceMonth,
                items = emptyList(),
                totalMonthlyDue = BigDecimal("23.89"),
                currency = Currency.BRL
            )
        )

        val request = SubscriberBillingEmailRequest(
            subscriberIds = listOf(1L),
            emails = listOf("kaique@example.com"),
            referenceMonth = "2026-04"
        )

        service.sendBillingSummaryToEmails(request)

        verify(billingService).getBillingForSubscriber(1L, referenceMonth)
        verify(billingService).getBillingForSubscriber(2L, referenceMonth)
        verify(billingService).getBillingForSubscriber(3L, referenceMonth)

        val htmlCaptor = argumentCaptor<String>()
        verify(emailService).sendHtml(
            eq("kaique@example.com"),
            any(),
            htmlCaptor.capture(),
            argThat { containsKey("pixQrCode") }
        )
        val normalizedHtml = htmlCaptor.firstValue.replace(Regex("\\s+"), " ")
        assertTrue(normalizedHtml.contains("Assinantes no resumo: <strong>3</strong>"))
        assertTrue(htmlCaptor.firstValue.contains("R$ 94.62"))
        assertTrue(!htmlCaptor.firstValue.contains("R$ 131.63"))
        assertTrue(htmlCaptor.firstValue.contains("Kaique"))
    }

    @Test
    fun `sendBillingSummaryToEmails with empty subscriber ids throws BadRequestApiException`() {
        val request = SubscriberBillingEmailRequest(subscriberIds = emptyList(), emails = listOf("owner@splitfy.com"))

        val exception = assertFailsWith<BadRequestApiException> {
            service.sendBillingSummaryToEmails(request)
        }

        assertTrue(exception.message!!.contains("subscriberIds"))
    }

    @Test
    fun `sendBillingSummaryToEmails uses provided reference month`() {
        val subscriber = sampleSubscriber()
        val requestedMonth = YearMonth.of(2025, 12)

        whenever(subscriberRepository.findAllById(listOf(1L))).thenReturn(listOf(subscriber))
        whenever(billingService.getBillingForSubscriber(eq(1L), eq(requestedMonth))).thenReturn(
            BillingResponse(
                userId = 1L,
                name = subscriber.name,
                email = subscriber.email,
                referenceMonth = requestedMonth,
                items = emptyList(),
                totalMonthlyDue = BigDecimal("0.00"),
                currency = Currency.BRL
            )
        )

        val request = SubscriberBillingEmailRequest(
            subscriberIds = listOf(1L),
            emails = listOf("finance@splitfy.com"),
            referenceMonth = "2025-12"
        )

        service.sendBillingSummaryToEmails(request)

        verify(billingService).getBillingForSubscriber(1L, requestedMonth)
    }

    @Test
    fun `sendBillingSummaryToEmails with invalid reference month throws BadRequestApiException`() {
        val request = SubscriberBillingEmailRequest(
            subscriberIds = listOf(1L),
            emails = listOf("owner@splitfy.com"),
            referenceMonth = "12-2025"
        )

        val exception = assertFailsWith<BadRequestApiException> {
            service.sendBillingSummaryToEmails(request)
        }

        assertTrue(exception.message!!.contains("Invalid referenceMonth"))
    }
}
