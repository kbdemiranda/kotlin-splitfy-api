package io.github.splitfy.api.service.payment

import io.github.splitfy.api.domain.entity.PaymentConfirmation
import io.github.splitfy.api.domain.entity.Subscriber
import io.github.splitfy.api.domain.entity.SubscriberPlatform
import io.github.splitfy.api.domain.enums.PaymentConfirmationStatus
import io.github.splitfy.api.domain.enums.ProfileName
import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.exception.ResourceNotFoundApiException
import io.github.splitfy.api.repository.PaymentConfirmationRepository
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.repository.SubscriberRepository
import io.github.splitfy.api.repository.UserRepository
import io.github.splitfy.api.service.email.EmailService
import io.github.splitfy.api.service.email.EmailTemplateService
import io.github.splitfy.api.web.subscriber.dto.PaymentConfirmationBatchRequest
import io.github.splitfy.api.web.subscriber.dto.PaymentConfirmationResponse
import io.github.splitfy.api.web.subscriber.dto.PendingPaymentApprovalResponse
import io.github.splitfy.api.web.subscriber.dto.PendingPaymentPlatform
import io.github.splitfy.api.web.subscriber.dto.PendingPaymentSubscriber
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.YearMonth

@Service
@Transactional
class PaymentConfirmationService(
    private val subscriberRepository: SubscriberRepository,
    private val subscriberPlatformRepository: SubscriberPlatformRepository,
    private val paymentConfirmationRepository: PaymentConfirmationRepository,
    private val userRepository: UserRepository,
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService
) {

    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    fun createConfirmations(request: PaymentConfirmationBatchRequest): List<PaymentConfirmationResponse> {
        if (request.confirmations.isEmpty()) {
            throw BadRequestApiException("confirmations must not be empty")
        }

        val actorEmail = currentUserEmail()
        val actorIsAdmin = isAdmin()
        val referenceMonth = parseReferenceMonth(request.referenceMonth)

        val responses = mutableListOf<PaymentConfirmationResponse>()
        val newPendings = mutableListOf<PaymentConfirmation>()

        request.confirmations.forEach { confirmationItem ->
            if (confirmationItem.platformIds.isEmpty()) {
                throw BadRequestApiException("platformIds must not be empty for subscriberId=${confirmationItem.subscriberId}")
            }

            val subscriber = loadSubscriber(confirmationItem.subscriberId)
            enforceNonAdminOwnership(actorIsAdmin, actorEmail, subscriber)

            confirmationItem.platformIds.distinct().forEach { platformId ->
                val association = subscriberPlatformRepository.findBySubscriberIdAndPlatformId(subscriber.id!!, platformId)
                    ?: throw BadRequestApiException(
                        "Subscriber ${subscriber.id} is not associated with platform id: $platformId"
                    )

                if (!association.isActive || association.deletedAt != null || association.platform.deletedAt != null) {
                    throw BadRequestApiException(
                        "Subscriber ${subscriber.id} is not actively associated with platform id: $platformId"
                    )
                }

                val upserted = upsertConfirmation(
                    actorIsAdmin = actorIsAdmin,
                    actorEmail = actorEmail,
                    referenceMonth = referenceMonth,
                    association = association
                )

                if (!actorIsAdmin && upserted.second) {
                    newPendings.add(upserted.first)
                }

                responses.add(toResponse(upserted.first))
            }
        }

        if (newPendings.isNotEmpty()) {
            notifyAdminsOfPendingConfirmations(newPendings, actorEmail, referenceMonth)
        }

        return responses
    }

    @PreAuthorize("hasRole('ADMIN')")
    fun approveConfirmation(confirmationId: Long): PaymentConfirmationResponse {
        val actorEmail = currentUserEmail()
        val confirmation = paymentConfirmationRepository.findByIdAndDeletedAtIsNull(confirmationId)
            ?: throw ResourceNotFoundApiException("Payment confirmation not found with id: $confirmationId")

        val now = LocalDateTime.now()
        val approved = if (confirmation.status == PaymentConfirmationStatus.CONFIRMED) {
            confirmation
        } else {
            paymentConfirmationRepository.save(
                confirmation.copy(
                    status = PaymentConfirmationStatus.CONFIRMED,
                    validatedByEmail = actorEmail,
                    validatedAt = now,
                    updatedAt = now
                )
            )
        }

        return toResponse(approved)
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    fun listPendingConfirmations(referenceMonth: String?): List<PendingPaymentApprovalResponse> {
        val pending = if (referenceMonth.isNullOrBlank()) {
            paymentConfirmationRepository.findAllPendingWithDetails(PaymentConfirmationStatus.PENDING)
        } else {
            val parsedMonth = parseReferenceMonth(referenceMonth)
            paymentConfirmationRepository.findAllPendingWithDetailsByReferenceMonth(
                status = PaymentConfirmationStatus.PENDING,
                referenceMonth = parsedMonth
            )
        }
        return pending.map { toPendingApprovalResponse(it) }
    }

    private fun upsertConfirmation(
        actorIsAdmin: Boolean,
        actorEmail: String,
        referenceMonth: YearMonth,
        association: SubscriberPlatform
    ): Pair<PaymentConfirmation, Boolean> {
        val now = LocalDateTime.now()
        val existing = paymentConfirmationRepository.findBySubscriberIdAndPlatformIdAndReferenceMonthAndDeletedAtIsNull(
            association.subscriber.id!!,
            association.platform.id!!,
            referenceMonth
        )

        if (existing == null) {
            val status = if (actorIsAdmin) PaymentConfirmationStatus.CONFIRMED else PaymentConfirmationStatus.PENDING
            val saved = paymentConfirmationRepository.save(
                PaymentConfirmation(
                    subscriber = association.subscriber,
                    platform = association.platform,
                    referenceMonth = referenceMonth,
                    status = status,
                    requestedByEmail = actorEmail,
                    requestedAt = now,
                    validatedByEmail = if (actorIsAdmin) actorEmail else null,
                    validatedAt = if (actorIsAdmin) now else null,
                    createdAt = now
                )
            )
            return Pair(saved, status == PaymentConfirmationStatus.PENDING)
        }

        if (existing.status == PaymentConfirmationStatus.CONFIRMED && !actorIsAdmin) {
            return Pair(existing, false)
        }

        val updated = if (actorIsAdmin) {
            existing.copy(
                status = PaymentConfirmationStatus.CONFIRMED,
                requestedByEmail = actorEmail,
                requestedAt = now,
                validatedByEmail = actorEmail,
                validatedAt = now,
                updatedAt = now
            )
        } else {
            existing.copy(
                status = PaymentConfirmationStatus.PENDING,
                requestedByEmail = actorEmail,
                requestedAt = now,
                validatedByEmail = null,
                validatedAt = null,
                updatedAt = now
            )
        }

        val saved = paymentConfirmationRepository.save(updated)
        val becamePending = !actorIsAdmin && existing.status != PaymentConfirmationStatus.PENDING
        return Pair(saved, becamePending)
    }

    private fun loadSubscriber(subscriberId: Long): Subscriber {
        return subscriberRepository.findById(subscriberId)
            .orElseThrow { ResourceNotFoundApiException("Subscriber not found with id: $subscriberId") }
            .also {
                if (it.deletedAt != null) {
                    throw ResourceNotFoundApiException("Subscriber not found with id: $subscriberId")
                }
            }
    }

    private fun enforceNonAdminOwnership(actorIsAdmin: Boolean, actorEmail: String, subscriber: Subscriber) {
        if (!actorIsAdmin && !subscriber.email.equals(actorEmail, ignoreCase = true)) {
            throw AccessDeniedException("You can only confirm payments for your own subscriber")
        }
    }

    private fun parseReferenceMonth(referenceMonth: String?): YearMonth {
        return if (referenceMonth.isNullOrBlank()) {
            YearMonth.now()
        } else {
            runCatching { YearMonth.parse(referenceMonth) }
                .getOrElse { throw BadRequestApiException("Invalid referenceMonth. Expected format: YYYY-MM") }
        }
    }

    private fun notifyAdminsOfPendingConfirmations(
        pendings: List<PaymentConfirmation>,
        actorEmail: String,
        referenceMonth: YearMonth
    ) {
        val adminEmails = userRepository
            .findActiveEmailsByProfileName(ProfileName.ADMIN)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (adminEmails.isEmpty()) {
            return
        }

        val subject = "Splitfy - confirmações pendentes de pagamento ($referenceMonth)"
        val htmlBody = buildPendingEmailHtml(pendings, actorEmail, referenceMonth)
        adminEmails.forEach { email -> emailService.sendHtml(email, subject, htmlBody) }
    }

    private fun buildPendingEmailHtml(
        pendings: List<PaymentConfirmation>,
        actorEmail: String,
        referenceMonth: YearMonth
    ): String {
        val bulletItems = pendings.map {
            "Subscriber ${it.subscriber.id} (${it.subscriber.name}) | Plataforma ${it.platform.id} (${it.platform.name})"
        }

        return emailTemplateService.render(
            preheader = "Novas confirmacoes pendentes aguardando aprovacao",
            heading = "Confirmacoes pendentes de pagamento",
            paragraphs = listOf(
                "Foram registradas confirmacoes de pagamento pendentes.",
                "Mes de referencia: $referenceMonth",
                "Solicitado por: $actorEmail"
            ),
            bulletItems = bulletItems,
            footer = "Acesse o sistema para validar os recebimentos."
        )
    }

    private fun currentUserEmail(): String {
        return SecurityContextHolder.getContext().authentication?.name
            ?.takeIf { it.isNotBlank() }
            ?: throw AccessDeniedException("Unauthenticated user")
    }

    private fun isAdmin(): Boolean {
        val authorities = SecurityContextHolder.getContext().authentication?.authorities.orEmpty()
        return authorities.any { it.authority == "ROLE_ADMIN" }
    }

    private fun toResponse(entity: PaymentConfirmation): PaymentConfirmationResponse {
        return PaymentConfirmationResponse(
            id = entity.id!!,
            subscriberId = entity.subscriber.id!!,
            platformId = entity.platform.id!!,
            referenceMonth = entity.referenceMonth,
            status = entity.status,
            requestedByEmail = entity.requestedByEmail,
            requestedAt = entity.requestedAt,
            validatedByEmail = entity.validatedByEmail,
            validatedAt = entity.validatedAt
        )
    }

    private fun toPendingApprovalResponse(entity: PaymentConfirmation): PendingPaymentApprovalResponse {
        return PendingPaymentApprovalResponse(
            confirmationId = entity.id!!,
            referenceMonth = entity.referenceMonth,
            status = entity.status,
            requestedByEmail = entity.requestedByEmail,
            requestedAt = entity.requestedAt,
            subscriber = PendingPaymentSubscriber(
                id = entity.subscriber.id!!,
                name = entity.subscriber.name,
                email = entity.subscriber.email
            ),
            platform = PendingPaymentPlatform(
                id = entity.platform.id!!,
                name = entity.platform.name,
                serviceType = entity.platform.serviceType,
                currency = entity.platform.currency,
                price = entity.platform.price
            )
        )
    }
}
