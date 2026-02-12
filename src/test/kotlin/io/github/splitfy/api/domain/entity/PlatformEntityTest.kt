package io.github.splitfy.api.domain.entity

import io.github.splitfy.api.domain.enums.BillingCycle
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class PlatformEntityTest {

    @Test
    fun `validateBillingDate allows null billingDate for MONTHLY`() {
        val p = Platform(
            id = 1L,
            platformToken = UUID.randomUUID(),
            name = "Test",
            price = BigDecimal.ONE,
            url = null,
            serviceType = io.github.splitfy.api.domain.enums.ServiceType.STREAMING_VIDEO,
            totalSlots = 5,
            availableSlots = 5,
            createdAt = LocalDateTime.now(),
            updatedAt = null,
            deletedAt = null,
            billingCycle = BillingCycle.MONTHLY,
            billingDate = null
        )

        // Should not throw
        p.validateBillingDate()
    }

    @Test
    fun `validateBillingDate throws when ANNUAL and billingDate null`() {
        val p = Platform(
            id = 1L,
            platformToken = UUID.randomUUID(),
            name = "Test",
            price = BigDecimal.ONE,
            url = null,
            serviceType = io.github.splitfy.api.domain.enums.ServiceType.STREAMING_VIDEO,
            totalSlots = 5,
            availableSlots = 5,
            createdAt = LocalDateTime.now(),
            updatedAt = null,
            deletedAt = null,
            billingCycle = BillingCycle.ANNUAL,
            billingDate = null
        )

        assertFailsWith<IllegalStateException> { p.validateBillingDate() }
    }
}
