package io.github.splitfy.api.service.billing

import io.github.splitfy.api.domain.entity.Platform
import io.github.splitfy.api.domain.entity.PaymentConfirmation
import io.github.splitfy.api.domain.entity.Subscriber
import io.github.splitfy.api.domain.entity.SubscriberPlatform
import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.domain.enums.Currency
import io.github.splitfy.api.domain.enums.PaymentConfirmationStatus
import io.github.splitfy.api.repository.PaymentConfirmationRepository
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.repository.SubscriberRepository
import io.github.splitfy.api.service.exchange.ExchangeRateQuote
import io.github.splitfy.api.service.exchange.ExchangeRateService
import io.github.splitfy.api.web.billing.dto.PaymentStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.YearMonth
import java.util.UUID

class BillingServiceTest {

    private val subscriberRepository: SubscriberRepository = mock()
    private val subscriberPlatformRepository: SubscriberPlatformRepository = mock()
    private val paymentConfirmationRepository: PaymentConfirmationRepository = mock()
    private val exchangeRateService: ExchangeRateService = mock()

    private lateinit var service: BillingServiceImpl

    @BeforeEach
    fun setup() {
        service = BillingServiceImpl(
            subscriberRepository,
            subscriberPlatformRepository,
            paymentConfirmationRepository,
            exchangeRateService
        )
    }

    private fun sampleSubscriber(id: Long): Subscriber {
        return Subscriber(
            id = id,
            subscriberToken = UUID.randomUUID(),
            name = "User",
            email = "u@example.com",
            createdAt = LocalDateTime.now()
        )
    }

    private fun samplePlatform(
        id: Long,
        name: String,
        price: BigDecimal,
        billingCycle: BillingCycle,
        billingDate: MonthDay?,
        currency: Currency = Currency.BRL
    ): Platform {
        return Platform(
            id = id,
            platformToken = UUID.randomUUID(),
            name = name,
            price = price,
            currency = currency,
            url = null,
            serviceType = io.github.splitfy.api.domain.enums.ServiceType.SOFTWARE,
            totalSlots = 5,
            availableSlots = 5,
            createdAt = LocalDateTime.now(),
            updatedAt = null,
            deletedAt = null,
            billingCycle = billingCycle,
            billingDate = billingDate
        )
    }

    private fun sampleAssoc(
        id: Long,
        subscriber: Subscriber,
        platform: Platform,
        subscribedAt: LocalDateTime = LocalDateTime.of(2025, 1, 1, 0, 0)
    ): SubscriberPlatform {
        return SubscriberPlatform(
            id = id,
            subscriber = subscriber,
            platform = platform,
            subscribedAt = subscribedAt,
            isActive = true,
            createdAt = LocalDateTime.now()
        )
    }

    @Test
    fun `monthly and annual included only in billing month sample`() {
        val subscriber = sampleSubscriber(1L)
        val netflix = samplePlatform(2L, "Netflix", BigDecimal("10.00"), BillingCycle.MONTHLY, null)
        val office = samplePlatform(3L, "Microsoft 365", BigDecimal("120.00"), BillingCycle.ANNUAL, MonthDay.of(1, 25))

        val assoc1 = sampleAssoc(1L, subscriber, netflix)
        val assoc2 = sampleAssoc(2L, subscriber, office)

        whenever(subscriberRepository.findById(1L)).thenReturn(java.util.Optional.of(subscriber))
        whenever(subscriberRepository.findByFinancialResponsibleSubscriberIdAndDeletedAtIsNull(1L)).thenReturn(emptyList())
        whenever(paymentConfirmationRepository.findBySubscriberIdInAndReferenceMonthAndDeletedAtIsNull(any(), any())).thenReturn(emptyList())
        whenever(subscriberPlatformRepository.findActiveBySubscriberIdWithPlatform(1L)).thenReturn(listOf(assoc1, assoc2))
        whenever(subscriberPlatformRepository.countActiveParticipantsByPlatformIds(listOf(2L, 3L))).thenReturn(listOf(
            object : SubscriberPlatformRepository.PlatformParticipantsCount { override fun getPlatformId() = 2L; override fun getCount() = 2L },
            object : SubscriberPlatformRepository.PlatformParticipantsCount { override fun getPlatformId() = 3L; override fun getCount() = 3L }
        ))
        whenever(subscriberPlatformRepository.countActiveParticipantsByPlatformIds(listOf(2L))).thenReturn(listOf(
            object : SubscriberPlatformRepository.PlatformParticipantsCount { override fun getPlatformId() = 2L; override fun getCount() = 2L }
        ))

        // reference month = January (office billing month) => total should include office monthly share
        val jan = YearMonth.of(2026, 1)
        val billingJan = service.getBillingForSubscriber(1L, jan)
        // netflix share = 10.00/2 = 5.00 ; office annual = 120.00 -> share = 120/3 = 40.00 -> total = 45.00
        assertEquals(BigDecimal("45.00"), billingJan.totalMonthlyDue)

        val officeItemJan = billingJan.items.first { it.serviceId == 3L }
        assertEquals(BigDecimal("120.00"), officeItemJan.serviceMonthlyAmount)
        assertEquals(BigDecimal("40.00"), officeItemJan.userMonthlyShare)

        // reference month = February => office should NOT be included
        val feb = YearMonth.of(2026, 2)
        val billingFeb = service.getBillingForSubscriber(1L, feb)
        // only netflix included -> 5.00
        assertEquals(BigDecimal("5.00"), billingFeb.totalMonthlyDue)
        assertFalse(billingFeb.items.any { it.serviceId == 3L })
    }

    @Test
    fun `foreign currency items are converted to BRL with latest quote`() {
        val subscriber = sampleSubscriber(1L)
        val notionUsd = samplePlatform(
            id = 4L,
            name = "Notion",
            price = BigDecimal("10.00"),
            billingCycle = BillingCycle.MONTHLY,
            billingDate = null,
            currency = Currency.USD
        )
        val assoc = sampleAssoc(1L, subscriber, notionUsd)
        val quoteAt = LocalDateTime.of(2026, 2, 12, 13, 4, 38)

        whenever(subscriberRepository.findById(1L)).thenReturn(java.util.Optional.of(subscriber))
        whenever(subscriberRepository.findByFinancialResponsibleSubscriberIdAndDeletedAtIsNull(1L)).thenReturn(emptyList())
        whenever(paymentConfirmationRepository.findBySubscriberIdInAndReferenceMonthAndDeletedAtIsNull(any(), any())).thenReturn(emptyList())
        whenever(subscriberPlatformRepository.findActiveBySubscriberIdWithPlatform(1L)).thenReturn(listOf(assoc))
        whenever(subscriberPlatformRepository.countActiveParticipantsByPlatformIds(listOf(4L))).thenReturn(listOf(
            object : SubscriberPlatformRepository.PlatformParticipantsCount {
                override fun getPlatformId() = 4L
                override fun getCount() = 2L
            }
        ))
        whenever(exchangeRateService.getLatestBrlRate(Currency.USD)).thenReturn(
            ExchangeRateQuote(
                currency = Currency.USD,
                rateToBrl = BigDecimal("5.1674"),
                quotedAt = quoteAt
            )
        )

        val billing = service.getBillingForSubscriber(1L, YearMonth.of(2026, 2))
        val item = billing.items.first()

        assertEquals(BigDecimal("51.67"), item.serviceMonthlyAmount)
        assertEquals(BigDecimal("25.84"), item.userMonthlyShare)
        assertEquals("USD", item.serviceCurrency)
        assertEquals(BigDecimal("10.00"), item.serviceMonthlyAmountOriginal)
        assertEquals(BigDecimal("5.00"), item.userMonthlyShareOriginal)
        assertEquals(BigDecimal("5.1674"), item.exchangeRateToBrl)
        assertEquals(quoteAt.toLocalDate(), item.exchangeRateDate)
        assertEquals(BigDecimal("25.84"), billing.totalMonthlyDue)
    }

    @Test
    fun `billing item exposes paid status when confirmation is approved`() {
        val subscriber = sampleSubscriber(1L)
        val netflix = samplePlatform(2L, "Netflix", BigDecimal("10.00"), BillingCycle.MONTHLY, null)
        val assoc = sampleAssoc(1L, subscriber, netflix)
        val refMonth = YearMonth.of(2026, 2)

        whenever(subscriberRepository.findById(1L)).thenReturn(java.util.Optional.of(subscriber))
        whenever(subscriberRepository.findByFinancialResponsibleSubscriberIdAndDeletedAtIsNull(1L)).thenReturn(emptyList())
        whenever(subscriberPlatformRepository.findActiveBySubscriberIdWithPlatform(1L)).thenReturn(listOf(assoc))
        whenever(subscriberPlatformRepository.countActiveParticipantsByPlatformIds(listOf(2L))).thenReturn(
            listOf(
                object : SubscriberPlatformRepository.PlatformParticipantsCount {
                    override fun getPlatformId() = 2L
                    override fun getCount() = 2L
                }
            )
        )
        whenever(paymentConfirmationRepository.findBySubscriberIdInAndReferenceMonthAndDeletedAtIsNull(listOf(1L), refMonth))
            .thenReturn(
                listOf(
                    PaymentConfirmation(
                        id = 1L,
                        subscriber = subscriber,
                        platform = netflix,
                        referenceMonth = refMonth,
                        status = PaymentConfirmationStatus.CONFIRMED,
                        requestedByEmail = "admin@splitfy.com",
                        requestedAt = LocalDateTime.now(),
                        validatedByEmail = "admin@splitfy.com",
                        validatedAt = LocalDateTime.now()
                    )
                )
            )

        val billing = service.getBillingForSubscriber(1L, refMonth)
        assertEquals(PaymentStatus.PAID, billing.items.first().paymentStatus)
    }

    @Test
    fun `billing aggregates responsible subscriber with covered subscribers`() {
        val responsible = sampleSubscriber(6L).copy(name = "Responsible")
        val coveredA = sampleSubscriber(1L).copy(name = "Covered A", financialResponsibleSubscriber = responsible)
        val coveredB = sampleSubscriber(4L).copy(name = "Covered B", financialResponsibleSubscriber = responsible)
        val platform = samplePlatform(20L, "1Password", BigDecimal("40.00"), BillingCycle.MONTHLY, null)

        val assocResponsible = sampleAssoc(10L, responsible, platform)
        val assocA = sampleAssoc(11L, coveredA, platform)
        val assocB = sampleAssoc(12L, coveredB, platform)

        whenever(subscriberRepository.findById(6L)).thenReturn(java.util.Optional.of(responsible))
        whenever(subscriberRepository.findByFinancialResponsibleSubscriberIdAndDeletedAtIsNull(6L))
            .thenReturn(listOf(coveredA, coveredB))
        whenever(paymentConfirmationRepository.findBySubscriberIdInAndReferenceMonthAndDeletedAtIsNull(listOf(6L, 1L, 4L), YearMonth.of(2026, 3)))
            .thenReturn(emptyList())
        whenever(subscriberPlatformRepository.findActiveBySubscriberIdWithPlatform(6L)).thenReturn(listOf(assocResponsible))
        whenever(subscriberPlatformRepository.findActiveBySubscriberIdWithPlatform(1L)).thenReturn(listOf(assocA))
        whenever(subscriberPlatformRepository.findActiveBySubscriberIdWithPlatform(4L)).thenReturn(listOf(assocB))
        whenever(subscriberPlatformRepository.countActiveParticipantsByPlatformIds(listOf(20L))).thenReturn(
            listOf(
                object : SubscriberPlatformRepository.PlatformParticipantsCount {
                    override fun getPlatformId() = 20L
                    override fun getCount() = 4L
                }
            )
        )

        val billing = service.getBillingForSubscriber(6L, YearMonth.of(2026, 3))
        val item = billing.items.first()

        assertEquals(BigDecimal("30.00"), billing.totalMonthlyDue)
        assertEquals(BigDecimal("30.00"), item.userMonthlyShare)
        assertEquals(3, item.coveredSubscribers.size)
        assertEquals(setOf(6L, 1L, 4L), item.coveredSubscribers.map { it.subscriberId }.toSet())
    }

    @Test
    fun `does not generate retroactive debt before association month`() {
        val subscriber = sampleSubscriber(1L)
        val netflix = samplePlatform(2L, "Netflix", BigDecimal("10.00"), BillingCycle.MONTHLY, null)
        val associationMonth = LocalDateTime.of(2026, 3, 10, 14, 0)
        val assoc = sampleAssoc(1L, subscriber, netflix, subscribedAt = associationMonth)

        whenever(subscriberRepository.findById(1L)).thenReturn(java.util.Optional.of(subscriber))
        whenever(subscriberRepository.findByFinancialResponsibleSubscriberIdAndDeletedAtIsNull(1L)).thenReturn(emptyList())
        whenever(paymentConfirmationRepository.findBySubscriberIdInAndReferenceMonthAndDeletedAtIsNull(listOf(1L), YearMonth.of(2026, 2)))
            .thenReturn(emptyList())
        whenever(subscriberPlatformRepository.findActiveBySubscriberIdWithPlatform(1L)).thenReturn(listOf(assoc))

        val billing = service.getBillingForSubscriber(1L, YearMonth.of(2026, 2))

        assertEquals(BigDecimal("0.00"), billing.totalMonthlyDue)
        assertEquals(0, billing.items.size)
    }

    @Test
    fun `association month starts next cycle when current month is already fully paid`() {
        val subscriber = sampleSubscriber(1L)
        val oldPlatform = samplePlatform(2L, "Old", BigDecimal("10.00"), BillingCycle.MONTHLY, null)
        val newPlatform = samplePlatform(3L, "New", BigDecimal("20.00"), BillingCycle.MONTHLY, null)
        val refMonth = YearMonth.of(2026, 3)
        val oldAssoc = sampleAssoc(1L, subscriber, oldPlatform, subscribedAt = LocalDateTime.of(2026, 1, 1, 0, 0))
        val newAssoc = sampleAssoc(2L, subscriber, newPlatform, subscribedAt = LocalDateTime.of(2026, 3, 10, 14, 0))

        whenever(subscriberRepository.findById(1L)).thenReturn(java.util.Optional.of(subscriber))
        whenever(subscriberRepository.findByFinancialResponsibleSubscriberIdAndDeletedAtIsNull(1L)).thenReturn(emptyList())
        whenever(subscriberPlatformRepository.findActiveBySubscriberIdWithPlatform(1L)).thenReturn(listOf(oldAssoc, newAssoc))
        whenever(subscriberPlatformRepository.countActiveParticipantsByPlatformIds(listOf(2L))).thenReturn(
            listOf(
                object : SubscriberPlatformRepository.PlatformParticipantsCount {
                    override fun getPlatformId() = 2L
                    override fun getCount() = 2L
                }
            )
        )
        whenever(paymentConfirmationRepository.findBySubscriberIdInAndReferenceMonthAndDeletedAtIsNull(listOf(1L), refMonth))
            .thenReturn(
                listOf(
                    PaymentConfirmation(
                        id = 1L,
                        subscriber = subscriber,
                        platform = oldPlatform,
                        referenceMonth = refMonth,
                        status = PaymentConfirmationStatus.CONFIRMED,
                        requestedByEmail = "admin@splitfy.com",
                        requestedAt = LocalDateTime.now(),
                        validatedByEmail = "admin@splitfy.com",
                        validatedAt = LocalDateTime.now()
                    )
                )
            )

        val billing = service.getBillingForSubscriber(1L, refMonth)

        assertEquals(setOf(2L), billing.items.map { it.serviceId }.toSet())
    }

    @Test
    fun `association month charges immediately when current month still has open debt`() {
        val subscriber = sampleSubscriber(1L)
        val oldPlatform = samplePlatform(2L, "Old", BigDecimal("10.00"), BillingCycle.MONTHLY, null)
        val newPlatform = samplePlatform(3L, "New", BigDecimal("20.00"), BillingCycle.MONTHLY, null)
        val refMonth = YearMonth.of(2026, 3)
        val oldAssoc = sampleAssoc(1L, subscriber, oldPlatform, subscribedAt = LocalDateTime.of(2026, 1, 1, 0, 0))
        val newAssoc = sampleAssoc(2L, subscriber, newPlatform, subscribedAt = LocalDateTime.of(2026, 3, 10, 14, 0))

        whenever(subscriberRepository.findById(1L)).thenReturn(java.util.Optional.of(subscriber))
        whenever(subscriberRepository.findByFinancialResponsibleSubscriberIdAndDeletedAtIsNull(1L)).thenReturn(emptyList())
        whenever(subscriberPlatformRepository.findActiveBySubscriberIdWithPlatform(1L)).thenReturn(listOf(oldAssoc, newAssoc))
        whenever(subscriberPlatformRepository.countActiveParticipantsByPlatformIds(listOf(2L, 3L))).thenReturn(
            listOf(
                object : SubscriberPlatformRepository.PlatformParticipantsCount {
                    override fun getPlatformId() = 2L
                    override fun getCount() = 2L
                },
                object : SubscriberPlatformRepository.PlatformParticipantsCount {
                    override fun getPlatformId() = 3L
                    override fun getCount() = 2L
                }
            )
        )
        whenever(paymentConfirmationRepository.findBySubscriberIdInAndReferenceMonthAndDeletedAtIsNull(listOf(1L), refMonth))
            .thenReturn(emptyList())

        val billing = service.getBillingForSubscriber(1L, refMonth)

        assertEquals(setOf(2L, 3L), billing.items.map { it.serviceId }.toSet())
    }
}
