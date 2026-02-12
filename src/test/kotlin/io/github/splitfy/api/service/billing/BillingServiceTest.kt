package io.github.splitfy.api.service.billing

import io.github.splitfy.api.domain.entity.Platform
import io.github.splitfy.api.domain.entity.Subscriber
import io.github.splitfy.api.domain.entity.SubscriberPlatform
import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.repository.SubscriberRepository
import org.junit.jupiter.api.Assertions.assertEquals
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

    private lateinit var service: BillingServiceImpl

    @BeforeEach
    fun setup() {
        service = BillingServiceImpl(subscriberRepository, subscriberPlatformRepository)
    }

    private fun sampleSubscriber(id: Long = 1L): Subscriber {
        return Subscriber(
            id = id,
            subscriberToken = UUID.randomUUID(),
            name = "User",
            email = "u@example.com",
            createdAt = LocalDateTime.now()
        )
    }

    private fun samplePlatform(id: Long, name: String, price: BigDecimal, billingCycle: BillingCycle, billingDate: MonthDay?): Platform {
        return Platform(
            id = id,
            platformToken = UUID.randomUUID(),
            name = name,
            price = price,
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

    private fun sampleAssoc(id: Long, subscriber: Subscriber, platform: Platform): SubscriberPlatform {
        return SubscriberPlatform(
            id = id,
            subscriber = subscriber,
            platform = platform,
            subscribedAt = LocalDateTime.now(),
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
        whenever(subscriberPlatformRepository.findActiveBySubscriberIdWithPlatform(1L)).thenReturn(listOf(assoc1, assoc2))
        whenever(subscriberPlatformRepository.countActiveParticipantsByPlatformIds(listOf(2L, 3L))).thenReturn(listOf(
            object : SubscriberPlatformRepository.PlatformParticipantsCount { override fun getPlatformId() = 2L; override fun getCount() = 2L },
            object : SubscriberPlatformRepository.PlatformParticipantsCount { override fun getPlatformId() = 3L; override fun getCount() = 3L }
        ))

        // reference month = January (office billing month) => total should include office monthly share
        val jan = YearMonth.of(2026, 1)
        val billingJan = service.getBillingForSubscriber(1L, jan)
        // netflix share = 10.00/2 = 5.00 ; office monthly = 120/12 = 10.00 -> share = 10/3 = 3.33 rounded -> total = 8.33
        assertEquals(BigDecimal("8.33"), billingJan.totalMonthlyDue)

        // reference month = February => office should NOT be included
        val feb = YearMonth.of(2026, 2)
        val billingFeb = service.getBillingForSubscriber(1L, feb)
        // only netflix included -> 5.00
        assertEquals(BigDecimal("5.00"), billingFeb.totalMonthlyDue)
    }
}

