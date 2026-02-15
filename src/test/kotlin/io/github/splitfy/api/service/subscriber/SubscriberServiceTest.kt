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
import io.github.splitfy.api.web.subscriber.dto.BillingItemDto
import io.github.splitfy.api.web.subscriber.dto.BillingResponse
import io.github.splitfy.api.web.subscriber.dto.Currency
import io.github.splitfy.api.web.subscriber.dto.PlatformAssociationRequest
import io.github.splitfy.api.web.subscriber.dto.SubscriberBillingEmailRequest
import org.mockito.kotlin.*
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
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
        emailTemplateService
    )

    private fun sampleSubscriber(): Subscriber {
        return Subscriber(
            id = 1L,
            subscriberToken = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            name = "User",
            email = "u@example.com",
            createdAt = LocalDateTime.now(),
            updatedAt = null,
            deletedAt = null
        )
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
    fun `sendBillingSummaryToEmails sends a single summary to all recipients`() {
        val subscriber1 = sampleSubscriber()
        val subscriber2 = sampleSubscriber().copy(id = 2L, name = "User 2", email = "u2@example.com")

        whenever(subscriberRepository.findAllById(listOf(1L, 2L))).thenReturn(listOf(subscriber1, subscriber2))
        whenever(billingService.getBillingForSubscriber(eq(1L), any())).thenReturn(
            BillingResponse(
                userId = 1L,
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
            argThat { this.contains("<html") }
        )
        verify(emailService).sendHtml(
            eq("owner@splitfy.com"),
            argThat { this.contains("Resumo de cobranças Splitfy") },
            any()
        )
    }

    @Test
    fun `sendBillingSummaryToEmails with empty subscriber ids throws BadRequestApiException`() {
        val request = SubscriberBillingEmailRequest(subscriberIds = emptyList(), emails = listOf("owner@splitfy.com"))

        val exception = assertFailsWith<BadRequestApiException> {
            service.sendBillingSummaryToEmails(request)
        }

        assertTrue(exception.message!!.contains("subscriberIds"))
    }
}
