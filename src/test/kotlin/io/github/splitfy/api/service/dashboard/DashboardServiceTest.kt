package io.github.splitfy.api.service.dashboard

import io.github.splitfy.api.domain.entity.PaymentConfirmation
import io.github.splitfy.api.domain.entity.Platform
import io.github.splitfy.api.domain.entity.Subscriber
import io.github.splitfy.api.domain.entity.SubscriberPlatform
import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.domain.enums.Currency
import io.github.splitfy.api.domain.enums.PaymentConfirmationStatus
import io.github.splitfy.api.domain.enums.ServiceType
import io.github.splitfy.api.repository.PaymentConfirmationRepository
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.service.exchange.ExchangeRateQuote
import io.github.splitfy.api.service.exchange.ExchangeRateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.YearMonth
import java.util.UUID

class DashboardServiceTest {

    private val subscriberPlatformRepository: SubscriberPlatformRepository = mock()
    private val paymentConfirmationRepository: PaymentConfirmationRepository = mock()
    private val exchangeRateService: ExchangeRateService = mock()

    private lateinit var service: DashboardService

    @BeforeEach
    fun setup() {
        service = DashboardService(
            subscriberPlatformRepository = subscriberPlatformRepository,
            paymentConfirmationRepository = paymentConfirmationRepository,
            exchangeRateService = exchangeRateService,
        )
    }

    @Test
    fun `getKpis aggregates due paid and consolidates all unpaid balances into pending`() {
        val refMonth = YearMonth.of(2026, 1)
        val subscriber1 = subscriber(1L, "a@example.com")
        val subscriber2 = subscriber(2L, "b@example.com")
        val subscriber3 = subscriber(3L, "c@example.com")
        val subscriber4 = subscriber(4L, "d@example.com")

        val monthlyPlatform = platform(
            id = 10L,
            name = "Netflix",
            price = BigDecimal("10.00"),
            currency = Currency.BRL,
            billingCycle = BillingCycle.MONTHLY,
            billingDate = null
        )
        val annualPlatform = platform(
            id = 20L,
            name = "Office",
            price = BigDecimal("120.00"),
            currency = Currency.BRL,
            billingCycle = BillingCycle.ANNUAL,
            billingDate = MonthDay.of(1, 25)
        )
        val usdPlatform = platform(
            id = 30L,
            name = "Notion",
            price = BigDecimal("10.00"),
            currency = Currency.USD,
            billingCycle = BillingCycle.MONTHLY,
            billingDate = null
        )

        val associations = listOf(
            assoc(1L, subscriber1, monthlyPlatform),
            assoc(2L, subscriber2, monthlyPlatform),
            assoc(3L, subscriber3, annualPlatform),
            assoc(4L, subscriber4, usdPlatform),
        )

        whenever(subscriberPlatformRepository.findAllActiveWithSubscriberAndPlatform()).thenReturn(associations)
        whenever(subscriberPlatformRepository.countActiveParticipantsByPlatformIds(listOf(10L, 20L, 30L))).thenReturn(
            listOf(
                count(platformId = 10L, count = 2L),
                count(platformId = 20L, count = 3L),
                count(platformId = 30L, count = 2L),
            )
        )
        whenever(exchangeRateService.getLatestBrlRate(Currency.USD)).thenReturn(
            ExchangeRateQuote(
                currency = Currency.USD,
                rateToBrl = BigDecimal("5.1674"),
                quotedAt = LocalDateTime.now()
            )
        )
        whenever(paymentConfirmationRepository.findByReferenceMonthAndDeletedAtIsNull(refMonth)).thenReturn(
            listOf(
                confirmation(
                    id = 1L,
                    subscriber = subscriber1,
                    platform = monthlyPlatform,
                    referenceMonth = refMonth,
                    status = PaymentConfirmationStatus.CONFIRMED
                ),
                confirmation(
                    id = 2L,
                    subscriber = subscriber2,
                    platform = monthlyPlatform,
                    referenceMonth = refMonth,
                    status = PaymentConfirmationStatus.PENDING
                ),
                confirmation(
                    id = 3L,
                    subscriber = subscriber4,
                    platform = usdPlatform,
                    referenceMonth = refMonth,
                    status = PaymentConfirmationStatus.PENDING
                ),
            )
        )

        val response = service.getKpis(refMonth)

        assertEquals(BigDecimal("75.84"), response.totalDue)
        assertEquals(BigDecimal("5.00"), response.totalPaid)
        assertEquals(BigDecimal("70.84"), response.totalPending)
        assertEquals(BigDecimal("0.00"), response.totalUnpaid)
        assertEquals(BigDecimal("93.41"), response.delinquencyRate)
        assertEquals(3, response.pendingByPlatform.size)
        assertEquals(20L, response.pendingByPlatform[0].platformId)
        assertEquals(BigDecimal("40.00"), response.pendingByPlatform[0].pendingAmount)
        assertEquals(30L, response.pendingByPlatform[1].platformId)
        assertEquals(BigDecimal("25.84"), response.pendingByPlatform[1].pendingAmount)
        assertEquals(10L, response.pendingByPlatform[2].platformId)
        assertEquals(BigDecimal("5.00"), response.pendingByPlatform[2].pendingAmount)
        assertEquals(3, response.debtors.size)
        assertEquals(3L, response.debtors[0].subscriberId)
        assertEquals(BigDecimal("40.00"), response.debtors[0].pendingAmount)
        assertEquals(BigDecimal("0.00"), response.debtors[0].unpaidAmount)
        assertEquals(BigDecimal("40.00"), response.debtors[0].totalDebt)
        assertEquals(4L, response.debtors[1].subscriberId)
        assertEquals(BigDecimal("25.84"), response.debtors[1].pendingAmount)
        assertEquals(BigDecimal("0.00"), response.debtors[1].unpaidAmount)
        assertEquals(BigDecimal("25.84"), response.debtors[1].totalDebt)
        assertEquals(2L, response.debtors[2].subscriberId)
        assertEquals(BigDecimal("5.00"), response.debtors[2].pendingAmount)
        assertEquals(BigDecimal("0.00"), response.debtors[2].unpaidAmount)
        assertEquals(BigDecimal("5.00"), response.debtors[2].totalDebt)
    }

    @Test
    fun `getKpis returns zeros when there are no active associations`() {
        whenever(subscriberPlatformRepository.findAllActiveWithSubscriberAndPlatform()).thenReturn(emptyList())

        val response = service.getKpis(YearMonth.of(2026, 2))

        assertEquals(BigDecimal("0.00"), response.totalDue)
        assertEquals(BigDecimal("0.00"), response.totalPaid)
        assertEquals(BigDecimal("0.00"), response.totalPending)
        assertEquals(BigDecimal("0.00"), response.totalUnpaid)
        assertEquals(BigDecimal("0.00"), response.delinquencyRate)
        assertEquals(0, response.pendingByPlatform.size)
        assertEquals(0, response.debtors.size)
    }

    @Test
    fun `getKpis treats dependent pending payment with same email as pending in dashboard`() {
        val refMonth = YearMonth.of(2026, 3)
        val holder = subscriber(1L, "family@example.com")
        val dependent = Subscriber(
            id = 2L,
            subscriberToken = UUID.randomUUID(),
            name = "Dependent",
            email = "family@example.com",
            createdAt = LocalDateTime.now(),
        )
        val platform = platform(
            id = 10L,
            name = "Netflix",
            price = BigDecimal("20.00"),
            currency = Currency.BRL,
            billingCycle = BillingCycle.MONTHLY,
            billingDate = null
        )

        whenever(subscriberPlatformRepository.findAllActiveWithSubscriberAndPlatform()).thenReturn(
            listOf(
                assoc(1L, holder, platform),
                assoc(2L, dependent, platform),
            )
        )
        whenever(subscriberPlatformRepository.countActiveParticipantsByPlatformIds(listOf(10L))).thenReturn(
            listOf(count(platformId = 10L, count = 2L))
        )
        whenever(paymentConfirmationRepository.findByReferenceMonthAndDeletedAtIsNull(refMonth)).thenReturn(
            listOf(
                confirmation(
                    id = 1L,
                    subscriber = holder,
                    platform = platform,
                    referenceMonth = refMonth,
                    status = PaymentConfirmationStatus.PENDING
                )
            )
        )

        val response = service.getKpis(refMonth)

        assertEquals(BigDecimal("20.00"), response.totalDue)
        assertEquals(BigDecimal("0.00"), response.totalPaid)
        assertEquals(BigDecimal("20.00"), response.totalPending)
        assertEquals(BigDecimal("0.00"), response.totalUnpaid)
        assertEquals(BigDecimal("100.00"), response.delinquencyRate)
        assertEquals(1, response.pendingByPlatform.size)
        assertEquals(BigDecimal("20.00"), response.pendingByPlatform[0].pendingAmount)
        assertEquals(2, response.debtors.size)
        assertEquals(BigDecimal("10.00"), response.debtors[0].pendingAmount)
        assertEquals(BigDecimal("0.00"), response.debtors[0].unpaidAmount)
        assertEquals(BigDecimal("10.00"), response.debtors[1].pendingAmount)
        assertEquals(BigDecimal("0.00"), response.debtors[1].unpaidAmount)
    }

    @Test
    fun `getKpis ignores associations before they start to avoid retroactive debt`() {
        val refMonth = YearMonth.of(2026, 2)
        val subscriber = subscriber(1L, "a@example.com")
        val platform = platform(
            id = 10L,
            name = "Netflix",
            price = BigDecimal("20.00"),
            currency = Currency.BRL,
            billingCycle = BillingCycle.MONTHLY,
            billingDate = null
        )

        whenever(subscriberPlatformRepository.findAllActiveWithSubscriberAndPlatform()).thenReturn(
            listOf(
                assoc(
                    id = 1L,
                    subscriber = subscriber,
                    platform = platform,
                    subscribedAt = LocalDateTime.of(2026, 3, 5, 8, 0)
                )
            )
        )
        whenever(paymentConfirmationRepository.findByReferenceMonthAndDeletedAtIsNull(refMonth)).thenReturn(emptyList())

        val response = service.getKpis(refMonth)

        assertEquals(BigDecimal("0.00"), response.totalDue)
        assertEquals(BigDecimal("0.00"), response.totalPending)
        assertEquals(0, response.pendingByPlatform.size)
        assertEquals(0, response.debtors.size)
    }

    private fun subscriber(id: Long, email: String): Subscriber {
        return Subscriber(
            id = id,
            subscriberToken = UUID.randomUUID(),
            name = "Subscriber $id",
            email = email,
            createdAt = LocalDateTime.now(),
        )
    }

    private fun platform(
        id: Long,
        name: String,
        price: BigDecimal,
        currency: Currency,
        billingCycle: BillingCycle,
        billingDate: MonthDay?,
    ): Platform {
        return Platform(
            id = id,
            platformToken = UUID.randomUUID(),
            name = name,
            price = price,
            currency = currency,
            url = null,
            serviceType = ServiceType.SOFTWARE,
            totalSlots = 10,
            availableSlots = 5,
            createdAt = LocalDateTime.now(),
            updatedAt = null,
            deletedAt = null,
            billingCycle = billingCycle,
            billingDate = billingDate,
        )
    }

    private fun assoc(
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
            createdAt = LocalDateTime.now(),
        )
    }

    private fun confirmation(
        id: Long,
        subscriber: Subscriber,
        platform: Platform,
        referenceMonth: YearMonth,
        status: PaymentConfirmationStatus,
    ): PaymentConfirmation {
        return PaymentConfirmation(
            id = id,
            subscriber = subscriber,
            platform = platform,
            referenceMonth = referenceMonth,
            status = status,
            requestedByEmail = "admin@splitfy.local",
            requestedAt = LocalDateTime.now(),
            validatedByEmail = if (status == PaymentConfirmationStatus.CONFIRMED) "admin@splitfy.local" else null,
            validatedAt = if (status == PaymentConfirmationStatus.CONFIRMED) LocalDateTime.now() else null,
        )
    }

    private fun count(platformId: Long, count: Long): SubscriberPlatformRepository.PlatformParticipantsCount {
        return object : SubscriberPlatformRepository.PlatformParticipantsCount {
            override fun getPlatformId(): Long = platformId
            override fun getCount(): Long = count
        }
    }
}
