package io.github.splitfy.api.service.platform

import io.github.splitfy.api.repository.PlatformRepository
import io.github.splitfy.api.web.platform.dto.PlatformRequest
import io.github.splitfy.api.domain.entity.Platform
import io.github.splitfy.api.domain.enums.ServiceType
import io.github.splitfy.api.domain.enums.BillingCycle
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import jakarta.persistence.EntityNotFoundException
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class PlatformServiceTest {

    private val platformRepository: PlatformRepository = mock()
    private val service = PlatformService(platformRepository)

    @Test
    fun `create saves and returns dto`() {
        val req = PlatformRequest(
            name = "Netflix",
            price = BigDecimal("29.90"),
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
        assertNotNull(resp.createdAt)
        // verify repository save called
        verify(platformRepository).save(any())
    }

    @Test
    fun `getPlatform not found throws EntityNotFoundException`() {
        whenever(platformRepository.findById(1L)).thenReturn(Optional.empty())

        assertFailsWith<EntityNotFoundException> {
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
    }
}
