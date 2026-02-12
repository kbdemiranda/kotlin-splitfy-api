package io.github.splitfy.api.service.subscriber

import io.github.splitfy.api.repository.SubscriberRepository
import io.github.splitfy.api.repository.PlatformRepository
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.domain.entity.Subscriber
import io.github.splitfy.api.domain.entity.Platform
import io.github.splitfy.api.domain.entity.SubscriberPlatform
import io.github.splitfy.api.web.subscriber.dto.PlatformAssociationRequest
import org.mockito.kotlin.*
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID
import org.apache.coyote.BadRequestException
import jakarta.persistence.EntityNotFoundException

class SubscriberServiceTest {

    private val subscriberRepository: SubscriberRepository = mock()
    private val platformRepository: PlatformRepository = mock()
    private val subscriberPlatformRepository: SubscriberPlatformRepository = mock()

    private val service = SubscriberService(subscriberRepository, platformRepository, subscriberPlatformRepository)

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
        whenever(subscriberPlatformRepository.existsBySubscriberIdAndPlatformId(1L, 2L)).thenReturn(false)
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

        assertFailsWith<EntityNotFoundException> {
            service.associatePlatforms(1L, req)
        }
    }

    @Test
    fun `associatePlatforms no available slots throws BadRequestException`() {
        whenever(subscriberRepository.findById(1L)).thenReturn(Optional.of(sampleSubscriber()))
        whenever(platformRepository.findById(2L)).thenReturn(Optional.of(samplePlatform(availableSlots = 0)))

        val req = listOf(PlatformAssociationRequest(platformIds = listOf(2L)))

        assertFailsWith<BadRequestException> {
            service.associatePlatforms(1L, req)
        }
    }

    @Test
    fun `associatePlatforms already associated throws BadRequestException`() {
        whenever(subscriberRepository.findById(1L)).thenReturn(Optional.of(sampleSubscriber()))
        whenever(platformRepository.findById(2L)).thenReturn(Optional.of(samplePlatform()))
        whenever(subscriberPlatformRepository.existsBySubscriberIdAndPlatformId(1L, 2L)).thenReturn(true)

        val req = listOf(PlatformAssociationRequest(platformIds = listOf(2L)))

        assertFailsWith<BadRequestException> {
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
        whenever(subscriberPlatformRepository.existsBySubscriberIdAndPlatformId(1L, 2L)).thenReturn(false)
        whenever(subscriberPlatformRepository.existsBySubscriberIdAndPlatformId(1L, 3L)).thenReturn(false)
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

        verify(subscriberPlatformRepository).save(argThat { isActive == false })
        verify(platformRepository).save(argThat { availableSlots == 2 })
    }

    @Test
    fun `disassociatePlatforms association missing throws BadRequestException`() {
        whenever(subscriberRepository.findById(1L)).thenReturn(Optional.of(sampleSubscriber()))
        whenever(platformRepository.findById(2L)).thenReturn(Optional.of(samplePlatform()))
        whenever(subscriberPlatformRepository.findBySubscriberIdAndPlatformId(1L, 2L)).thenReturn(null)

        val req = listOf(PlatformAssociationRequest(platformIds = listOf(2L)))

        assertFailsWith<BadRequestException> {
            service.disassociatePlatforms(1L, req)
        }
    }
}
