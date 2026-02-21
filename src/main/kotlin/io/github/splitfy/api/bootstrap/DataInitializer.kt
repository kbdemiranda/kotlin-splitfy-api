package io.github.splitfy.api.bootstrap

import io.github.splitfy.api.domain.entity.Platform
import io.github.splitfy.api.domain.entity.Subscriber
import io.github.splitfy.api.domain.entity.SubscriberPlatform
import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.domain.enums.ServiceType
import io.github.splitfy.api.repository.PlatformRepository
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.repository.SubscriberRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.MonthDay
import java.util.UUID
import kotlin.random.Random

@Component
@Profile("dev")
class DataInitializer(
    private val platformRepository: PlatformRepository,
    private val subscriberRepository: SubscriberRepository,
    private val subscriberPlatformRepository: SubscriberPlatformRepository
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(DataInitializer::class.java)

    @Transactional
    override fun run(args: ApplicationArguments) {
//        val platforms = initPlatform()
//        val subscribers = initSubscriber()
//        initRandomAssociations(subscribers, platforms)
    }

    private fun initSubscriber(): List<Subscriber> {
        if (subscriberRepository.count() > 0) {
            logger.info("Subscribers already exist, skipping data initialization")
            return subscriberRepository.findAll()
        }

        val subscribers = listOf(
            Subscriber(
                subscriberToken = UUID.randomUUID(),
                name = "Ana Souza",
                email = "ana.souza@example.com"
            ),
            Subscriber(
                subscriberToken = UUID.randomUUID(),
                name = "Bruno Lima",
                email = "bruno.lima@example.com"
            ),
            Subscriber(
                subscriberToken = UUID.randomUUID(),
                name = "Carla Mendes",
                email = "carla.mendes@example.com"
            ),
            Subscriber(
                subscriberToken = UUID.randomUUID(),
                name = "Diego Rocha",
                email = "diego.rocha@example.com"
            ),
            Subscriber(
                subscriberToken = UUID.randomUUID(),
                name = "Elisa Prado",
                email = "elisa.prado@example.com"
            )
        )

        val savedSubscribers = subscriberRepository.saveAll(subscribers)
        logger.info("Inserted ${savedSubscribers.size} sample subscribers")
        return savedSubscribers
    }

    private fun initPlatform(): List<Platform> {
        if (platformRepository.count() > 0) {
            logger.info("Platforms already exist, skipping data initialization")
            return platformRepository.findAll()
        }

        val platforms = listOf(
            Platform(
                platformToken = UUID.randomUUID(),
                name = "HBO Max",
                price = BigDecimal("27.93"),
                url = "https://hbomax.com/",
                serviceType = ServiceType.STREAMING_VIDEO,
                totalSlots = 5,
                availableSlots = 5,
                billingCycle = BillingCycle.MONTHLY
            ),
            Platform(
                platformToken = UUID.randomUUID(),
                name = "Prime Video",
                price = BigDecimal("0.00"),
                url = "https://www.primevideo.com/",
                serviceType = ServiceType.STREAMING_VIDEO,
                totalSlots = 5,
                availableSlots = 5,
                billingCycle = BillingCycle.MONTHLY
            ),
            Platform(
                platformToken = UUID.randomUUID(),
                name = "Spotify",
                price = BigDecimal("40.90"),
                url = "https://spotify.com/",
                serviceType = ServiceType.STREAMING_MUSIC,
                totalSlots = 6,
                availableSlots = 6,
                billingCycle = BillingCycle.MONTHLY
            ),
            Platform(
                platformToken = UUID.randomUUID(),
                name = "Netflix",
                price = BigDecimal("24.00"),
                url = "https://www.netflix.com/br/",
                serviceType = ServiceType.STREAMING_VIDEO,
                totalSlots = 5,
                availableSlots = 5,
                billingCycle = BillingCycle.MONTHLY
            ),
            Platform(
                platformToken = UUID.randomUUID(),
                name = "Microsoft 365 Family",
                price = BigDecimal("599.00"),
                url = "https://www.office.com/",
                serviceType = ServiceType.SOFTWARE,
                totalSlots = 6,
                availableSlots = 6,
                billingCycle = BillingCycle.ANNUAL,
                billingDate = MonthDay.of(2, 1)
            )
        )

        val savedPlatforms = platformRepository.saveAll(platforms)
        logger.info("Inserted ${savedPlatforms.size} sample platforms")
        return savedPlatforms
    }

    private fun initRandomAssociations(subscribers: List<Subscriber>, platforms: List<Platform>) {
        if (subscribers.isEmpty() || platforms.isEmpty()) {
            logger.info("No subscribers/platforms found, skipping associations initialization")
            return
        }

        if (subscriberPlatformRepository.count() > 0) {
            logger.info("Subscriber-platform associations already exist, skipping initialization")
            return
        }

        val random = Random(System.currentTimeMillis())
        val remainingSlotsByPlatformId = platforms
            .mapNotNull { platform -> platform.id?.let { it to platform.totalSlots } }
            .toMap()
            .toMutableMap()
        val associations = mutableListOf<SubscriberPlatform>()

        subscribers.forEach { subscriber ->
            val subscriberId = subscriber.id ?: return@forEach
            val candidatePlatforms = platforms.shuffled(random).toMutableList()
            val targetAssociations = random.nextInt(from = 1, until = 4)
            var created = 0

            while (candidatePlatforms.isNotEmpty() && created < targetAssociations) {
                val platform = candidatePlatforms.removeAt(0)
                val platformId = platform.id ?: continue
                val remainingSlots = remainingSlotsByPlatformId[platformId] ?: 0

                if (remainingSlots <= 0) {
                    continue
                }

                val alreadyAssociated = associations.any {
                    it.subscriber.id == subscriberId && it.platform.id == platformId
                }
                if (alreadyAssociated) {
                    continue
                }

                associations.add(
                    SubscriberPlatform(
                        subscriber = subscriber,
                        platform = platform,
                        createdAt = LocalDateTime.now()
                    )
                )
                remainingSlotsByPlatformId[platformId] = remainingSlots - 1
                created++
            }
        }

        if (associations.isEmpty()) {
            logger.info("No random associations generated")
            return
        }

        subscriberPlatformRepository.saveAll(associations)

        val updatedPlatforms = platforms.map { platform ->
            val platformId = platform.id ?: return@map platform
            val remaining = remainingSlotsByPlatformId[platformId] ?: platform.totalSlots
            platform.copy(
                availableSlots = remaining,
                updatedAt = LocalDateTime.now()
            )
        }
        platformRepository.saveAll(updatedPlatforms)

        logger.info("Inserted ${associations.size} random subscriber-platform associations")
    }
}
