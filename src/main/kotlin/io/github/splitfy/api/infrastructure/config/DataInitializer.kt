package io.github.splitfy.api.infrastructure.config

import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.domain.enums.ServiceType
import io.github.splitfy.api.domain.models.Platform
import io.github.splitfy.api.domain.repository.PlatformRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.MonthDay

@Component
@Profile("dev")
class DataInitializer(private val platformRepository: PlatformRepository) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(DataInitializer::class.java)

    override fun run(args: ApplicationArguments) {
        if (platformRepository.count() > 0) {
            logger.info("Platforms already exist, skipping data initialization")
            return
        }

        val platforms = listOf(
            Platform(
                name = "Netflix",
                price = BigDecimal("19.90"),
                url = "https://www.netflix.com",
                serviceType = ServiceType.STREAMING_VIDEO,
                totalSlots = 4,
                availableSlots = 2,
                billingCycle = BillingCycle.MONTHLY
            ),
            Platform(
                name = "Spotify",
                price = BigDecimal("9.99"),
                url = "https://www.spotify.com",
                serviceType = ServiceType.STREAMING_MUSIC,
                totalSlots = 6,
                availableSlots = 5,
                billingCycle = BillingCycle.MONTHLY
            ),
            Platform(
                name = "GitHub",
                price = BigDecimal("7.00"),
                url = "https://github.com",
                serviceType = ServiceType.SOFTWARE,
                totalSlots = 1,
                availableSlots = 1,
                billingCycle = BillingCycle.MONTHLY
            ),
            Platform(
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

