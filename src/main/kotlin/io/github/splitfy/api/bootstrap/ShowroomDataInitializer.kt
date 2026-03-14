package io.github.splitfy.api.bootstrap

import io.github.splitfy.api.domain.entity.PaymentConfirmation
import io.github.splitfy.api.domain.entity.Platform
import io.github.splitfy.api.domain.entity.Subscriber
import io.github.splitfy.api.domain.entity.SubscriberPlatform
import io.github.splitfy.api.domain.entity.User
import io.github.splitfy.api.domain.enums.BillingCycle
import io.github.splitfy.api.domain.enums.Currency
import io.github.splitfy.api.domain.enums.PaymentConfirmationStatus
import io.github.splitfy.api.domain.enums.ProfileName
import io.github.splitfy.api.domain.enums.ServiceType
import io.github.splitfy.api.repository.PaymentConfirmationRepository
import io.github.splitfy.api.repository.PlatformRepository
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.repository.SubscriberRepository
import io.github.splitfy.api.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile as SpringProfile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.YearMonth
import java.util.UUID

@Component
@SpringProfile("showroom")
class ShowroomDataInitializer(
    private val defaultProfileBootstrap: DefaultProfileBootstrap,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val platformRepository: PlatformRepository,
    private val subscriberRepository: SubscriberRepository,
    private val subscriberPlatformRepository: SubscriberPlatformRepository,
    private val paymentConfirmationRepository: PaymentConfirmationRepository
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(ShowroomDataInitializer::class.java)
    private val subscriberSeeds = listOf(
        subscriberSeed("Ana Souza", "ana.souza@splitfy.local"),
        subscriberSeed("Bruno Lima", "bruno.lima@splitfy.local"),
        subscriberSeed("Carla Mendes", "carla.mendes@splitfy.local"),
        subscriberSeed("Daniel Rocha", "daniel.rocha@splitfy.local"),
        subscriberSeed("Elisa Prado", "elisa.prado@splitfy.local"),
        subscriberSeed("Fabio Alves", "fabio.alves@splitfy.local"),
        subscriberSeed("Gabriela Costa", "gabriela.costa@splitfy.local"),
        subscriberSeed("Helena Martins", "helena.martins@splitfy.local"),
        subscriberSeed("Igor Nunes", "igor.nunes@splitfy.local"),
        subscriberSeed("Juliana Freitas", "juliana.freitas@splitfy.local"),
        subscriberSeed("Kaio Barros", "kaio.barros@splitfy.local"),
        subscriberSeed("Larissa Campos", "larissa.campos@splitfy.local")
    )

    @Transactional
    override fun run(args: ApplicationArguments) {
        val profiles = defaultProfileBootstrap.ensureProfiles()
        if (platformRepository.count() > 0L || subscriberRepository.count() > 0L || subscriberPlatformRepository.count() > 0L) {
            logger.info("Showroom data already exists, skipping initialization")
            return
        }

        val users = initUsers(profiles)
        val platforms = initPlatforms()
        val subscribers = initSubscribers()
        val associations = initAssociations(subscribers, platforms)
        val paymentConfirmations = initPaymentConfirmations(associations)

        logger.info(
            "Showroom data initialized: {} users, {} platforms, {} subscribers, {} associations, {} payment confirmations",
            users.size,
            platforms.size,
            subscribers.size,
            associations.size,
            paymentConfirmations.size
        )
    }

    private fun initUsers(profiles: List<io.github.splitfy.api.domain.entity.Profile>): List<User> {
        val profilesByName = profiles.associateBy { it.name }
        val defaultPassword = passwordEncoder.encode("admin")
            ?: throw IllegalStateException("Password encoding failed for showroom users")

        val viewerProfile = profilesByName[ProfileName.VIEWER]
            ?: throw IllegalStateException("Profile VIEWER not found for showroom bootstrap")

        val users = subscriberSeeds.map { seed ->
            User(
                name = seed.name,
                email = seed.email,
                password = defaultPassword,
                profile = viewerProfile,
                isEnabled = true,
                receivesDashboardEmail = false
            )
        }

        return userRepository.saveAll(users)
    }

    private fun initPlatforms(): List<Platform> {
        val platformSeeds = listOf(
            platformSeed("Netflix", "59.90", Currency.BRL, ServiceType.STREAMING_VIDEO, 4, BillingCycle.MONTHLY, "https://www.netflix.com/br/"),
            platformSeed("Spotify", "34.90", Currency.BRL, ServiceType.STREAMING_MUSIC, 6, BillingCycle.MONTHLY, "https://www.spotify.com/br/family/"),
            platformSeed("YouTube Premium", "41.90", Currency.BRL, ServiceType.STREAMING_VIDEO, 5, BillingCycle.MONTHLY, "https://www.youtube.com/premium"),
            platformSeed("HBO Max", "39.90", Currency.BRL, ServiceType.STREAMING_VIDEO, 4, BillingCycle.MONTHLY, "https://www.max.com/br/"),
            platformSeed("Prime Video", "19.90", Currency.BRL, ServiceType.STREAMING_VIDEO, 4, BillingCycle.MONTHLY, "https://www.primevideo.com/"),
            platformSeed("Nintendo", "119.00", Currency.BRL, ServiceType.GAMES, 8, BillingCycle.ANNUAL, "https://www.nintendo.com/pt-br/switch/online/", MonthDay.of(7, 10)),
            platformSeed("Microsoft 365", "599.00", Currency.BRL, ServiceType.SOFTWARE, 6, BillingCycle.ANNUAL, "https://www.microsoft.com/pt-br/microsoft-365/family", MonthDay.of(2, 1)),
            platformSeed("Google One", "38.99", Currency.BRL, ServiceType.CLOUD_STORAGE, 5, BillingCycle.MONTHLY, "https://one.google.com/"),
            platformSeed("Duolingo", "149.90", Currency.BRL, ServiceType.SOFTWARE, 6, BillingCycle.ANNUAL, "https://www.duolingo.com/super", MonthDay.of(9, 5)),
            platformSeed("The New York Times", "12.00", Currency.USD, ServiceType.NEWS, 4, BillingCycle.MONTHLY, "https://www.nytimes.com/subscription"),
            platformSeed("OpenAI", "20.00", Currency.USD, ServiceType.SOFTWARE, 3, BillingCycle.MONTHLY, "https://chatgpt.com/")
        )

        val platforms = platformSeeds.map { seed ->
            Platform(
                platformToken = UUID.randomUUID(),
                name = seed.name,
                price = BigDecimal(seed.price),
                currency = seed.currency,
                url = seed.url,
                serviceType = seed.serviceType,
                totalSlots = seed.totalSlots,
                availableSlots = seed.totalSlots,
                billingCycle = seed.billingCycle,
                billingDate = seed.billingDate
            )
        }

        return platformRepository.saveAll(platforms)
    }

    private fun initSubscribers(): List<Subscriber> {
        val subscribers = subscriberSeeds.map { seed ->
            Subscriber(
                subscriberToken = UUID.randomUUID(),
                name = seed.name,
                email = seed.email
            )
        }

        return subscriberRepository.saveAll(subscribers)
    }

    private fun initAssociations(subscribers: List<Subscriber>, platforms: List<Platform>): List<SubscriberPlatform> {
        val subscribersByEmail = subscribers.associateBy { it.email }
        val platformsByName = platforms.associateBy { it.name }
        val associationSeeds = listOf(
            associationSeed("ana.souza@splitfy.local", "Netflix"),
            associationSeed("ana.souza@splitfy.local", "Spotify"),
            associationSeed("ana.souza@splitfy.local", "Google One"),
            associationSeed("bruno.lima@splitfy.local", "Prime Video"),
            associationSeed("bruno.lima@splitfy.local", "YouTube Premium"),
            associationSeed("carla.mendes@splitfy.local", "HBO Max"),
            associationSeed("carla.mendes@splitfy.local", "Duolingo"),
            associationSeed("carla.mendes@splitfy.local", "Nintendo"),
            associationSeed("daniel.rocha@splitfy.local", "Netflix"),
            associationSeed("daniel.rocha@splitfy.local", "Microsoft 365"),
            associationSeed("elisa.prado@splitfy.local", "Spotify"),
            associationSeed("elisa.prado@splitfy.local", "Google One"),
            associationSeed("fabio.alves@splitfy.local", "YouTube Premium"),
            associationSeed("fabio.alves@splitfy.local", "Prime Video"),
            associationSeed("gabriela.costa@splitfy.local", "Netflix"),
            associationSeed("gabriela.costa@splitfy.local", "The New York Times"),
            associationSeed("helena.martins@splitfy.local", "Spotify"),
            associationSeed("helena.martins@splitfy.local", "HBO Max"),
            associationSeed("igor.nunes@splitfy.local", "Nintendo"),
            associationSeed("igor.nunes@splitfy.local", "Microsoft 365"),
            associationSeed("juliana.freitas@splitfy.local", "Duolingo"),
            associationSeed("juliana.freitas@splitfy.local", "Google One"),
            associationSeed("kaio.barros@splitfy.local", "Prime Video"),
            associationSeed("kaio.barros@splitfy.local", "The New York Times"),
            associationSeed("larissa.campos@splitfy.local", "Netflix"),
            associationSeed("larissa.campos@splitfy.local", "Spotify"),
            associationSeed("larissa.campos@splitfy.local", "YouTube Premium"),
            associationSeed("larissa.campos@splitfy.local", "Microsoft 365"),
            associationSeed("ana.souza@splitfy.local", "OpenAI"),
            associationSeed("gabriela.costa@splitfy.local", "OpenAI"),
            associationSeed("juliana.freitas@splitfy.local", "OpenAI")
        )

        val associations = associationSeeds.mapIndexed { index, seed ->
            val subscribedAt = LocalDateTime.now().minusDays((associationSeeds.size - index).toLong())
            SubscriberPlatform(
                subscriber = subscribersByEmail[seed.subscriberEmail]
                    ?: throw IllegalStateException("Subscriber ${seed.subscriberEmail} not found for showroom bootstrap"),
                platform = platformsByName[seed.platformName]
                    ?: throw IllegalStateException("Platform ${seed.platformName} not found for showroom bootstrap"),
                subscribedAt = subscribedAt,
                createdAt = subscribedAt
            )
        }

        val savedAssociations = subscriberPlatformRepository.saveAll(associations)
        val activeUsageByPlatformId = savedAssociations.groupingBy { it.platform.id }.eachCount()
        val updatedPlatforms = platforms.map { platform ->
            val activeUsages = activeUsageByPlatformId[platform.id] ?: 0
            platform.copy(
                availableSlots = (platform.totalSlots - activeUsages).coerceAtLeast(0),
                updatedAt = LocalDateTime.now()
            )
        }
        platformRepository.saveAll(updatedPlatforms)

        return savedAssociations
    }

    private fun initPaymentConfirmations(associations: List<SubscriberPlatform>): List<PaymentConfirmation> {
        val now = LocalDateTime.now()
        val currentMonth = YearMonth.now()
        val previousMonth = currentMonth.minusMonths(1)

        val currentMonthPayments = associations.take(14).mapIndexed { index, association ->
            val status = if (index % 4 == 0) PaymentConfirmationStatus.PENDING else PaymentConfirmationStatus.CONFIRMED
            paymentConfirmationOf(
                association = association,
                referenceMonth = currentMonth,
                status = status,
                requestedAt = now.minusDays((index + 1).toLong()),
                requestedByEmail = "billing@splitfy.local"
            )
        }

        val previousMonthPayments = associations.drop(6).take(12).mapIndexed { index, association ->
            val status = if (index % 5 == 0) PaymentConfirmationStatus.PENDING else PaymentConfirmationStatus.CONFIRMED
            paymentConfirmationOf(
                association = association,
                referenceMonth = previousMonth,
                status = status,
                requestedAt = now.minusDays((index + 18).toLong()),
                requestedByEmail = "billing@splitfy.local"
            )
        }

        return paymentConfirmationRepository.saveAll(currentMonthPayments + previousMonthPayments)
    }

    private fun paymentConfirmationOf(
        association: SubscriberPlatform,
        referenceMonth: YearMonth,
        status: PaymentConfirmationStatus,
        requestedAt: LocalDateTime,
        requestedByEmail: String
    ): PaymentConfirmation {
        val validatedAt = if (status == PaymentConfirmationStatus.CONFIRMED) requestedAt.plusHours(6) else null

        return PaymentConfirmation(
            subscriber = association.subscriber,
            platform = association.platform,
            referenceMonth = referenceMonth,
            status = status,
            requestedByEmail = requestedByEmail,
            requestedAt = requestedAt,
            validatedByEmail = if (validatedAt != null) "admin@splitfy.local" else null,
            validatedAt = validatedAt,
            createdAt = requestedAt,
            updatedAt = validatedAt ?: requestedAt
        )
    }

    private fun platformSeed(
        name: String,
        price: String,
        currency: Currency,
        serviceType: ServiceType,
        totalSlots: Int,
        billingCycle: BillingCycle,
        url: String,
        billingDate: MonthDay? = null
    ) = PlatformSeed(name, price, currency, serviceType, totalSlots, billingCycle, url, billingDate)

    private fun subscriberSeed(name: String, email: String) = SubscriberSeed(name, email)

    private fun associationSeed(subscriberEmail: String, platformName: String) =
        AssociationSeed(subscriberEmail, platformName)

    private data class PlatformSeed(
        val name: String,
        val price: String,
        val currency: Currency,
        val serviceType: ServiceType,
        val totalSlots: Int,
        val billingCycle: BillingCycle,
        val url: String,
        val billingDate: MonthDay?
    )

    private data class SubscriberSeed(
        val name: String,
        val email: String
    )

    private data class AssociationSeed(
        val subscriberEmail: String,
        val platformName: String
    )
}
