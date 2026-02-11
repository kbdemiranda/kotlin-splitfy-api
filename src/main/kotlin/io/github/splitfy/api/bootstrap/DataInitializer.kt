package io.github.splitfy.api.bootstrap

import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.domain.enums.ServiceType
import io.github.splitfy.api.domain.entity.Platform
import io.github.splitfy.api.domain.entity.Subscriber
import io.github.splitfy.api.repository.PlatformRepository
import io.github.splitfy.api.repository.SubscriberRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.MonthDay
import java.util.UUID

@Component
@Profile("dev")
class DataInitializer(
    private val platformRepository: PlatformRepository,
    private val subscriberRepository: SubscriberRepository
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(DataInitializer::class.java)

    override fun run(args: ApplicationArguments) {
        initPlatform()
        initSubscriber()
    }

    private fun initSubscriber() {
        if (subscriberRepository.count() > 0){
            logger.info("Subscribers already exist, skipping data initialization")
            return
        }

        val subscribers = listOf(
            Subscriber(
                subscriberToken = UUID.randomUUID(),
                name = "John Doe",
                email = "john.doe@example.com",
            ),
            Subscriber(
                subscriberToken = UUID.randomUUID(),
                name = "Maria Doe",
                email = "maria@example.com"
            )
        )

        subscriberRepository.saveAll(subscribers)
        logger.info("Subscribers data initialized successfully")
    }

    private fun initPlatform() {
        if (platformRepository.count() > 0) {
            logger.info("Platforms already exist, skipping data initialization")
            return
        }

        val platforms = listOf(
            Platform(
                platformToken = UUID.randomUUID(),
                name = "Netflix",
                price = BigDecimal("19.90"),
                url = "https://www.netflix.com",
                serviceType = ServiceType.STREAMING_VIDEO,
                totalSlots = 4,
                availableSlots = 2,
                billingCycle = BillingCycle.MONTHLY
            ),
            Platform(
                platformToken = UUID.randomUUID(),
                name = "Spotify",
                price = BigDecimal("9.99"),
                url = "https://www.spotify.com",
                serviceType = ServiceType.STREAMING_MUSIC,
                totalSlots = 6,
                availableSlots = 5,
                billingCycle = BillingCycle.MONTHLY
            ),
            Platform(
                platformToken = UUID.randomUUID(),
                name = "GitHub",
                price = BigDecimal("7.00"),
                url = "https://github.com",
                serviceType = ServiceType.SOFTWARE,
                totalSlots = 1,
                availableSlots = 1,
                billingCycle = BillingCycle.MONTHLY
            ),
            Platform(
                platformToken = UUID.randomUUID(),
                name = "Microsoft 365",
                price = BigDecimal("699.00"),
                url = "https://office.com",
                serviceType = ServiceType.SOFTWARE,
                totalSlots = 6,
                availableSlots = 6,
                billingCycle = BillingCycle.ANNUAL,
                billingDate = MonthDay.of(1, 25)
            )
        )

        platformRepository.saveAll(platforms)
        logger.info("Inserted ${platforms.size} sample platforms into H2 database")
    }
}
