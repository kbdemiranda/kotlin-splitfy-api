package io.github.splitfy.api.service.payment

import io.github.splitfy.api.domain.entity.PaymentConfirmation
import io.github.splitfy.api.domain.entity.Subscriber
import io.github.splitfy.api.domain.entity.SubscriberPlatform
import io.github.splitfy.api.domain.enums.PaymentConfirmationStatus
import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.exception.ResourceNotFoundApiException
import io.github.splitfy.api.logging.infoEvent
import io.github.splitfy.api.repository.PaymentConfirmationRepository
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.repository.SubscriberRepository
import io.github.splitfy.api.web.paymnets.dto.PaymentConfirmationPlatformsRequest
import io.github.splitfy.api.web.paymnets.dto.PaymentConfirmationResponse
import io.github.splitfy.api.web.paymnets.dto.PendingPaymentApprovalResponse
import io.github.splitfy.api.web.paymnets.dto.PendingPaymentPlatform
import io.github.splitfy.api.web.paymnets.dto.PendingPaymentSubscriber
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.YearMonth

@Service
@Transactional
class PaymentConfirmationService(
    private val subscriberRepository: SubscriberRepository,
    private val subscriberPlatformRepository: SubscriberPlatformRepository,
    private val paymentConfirmationRepository: PaymentConfirmationRepository
) {
    private val log = LoggerFactory.getLogger(PaymentConfirmationService::class.java)

    @PreAuthorize("hasRole('ADMIN')")
    fun createAdminConfirmations(
        subscriberId: Long,
        request: PaymentConfirmationPlatformsRequest
    ): List<PaymentConfirmationResponse> {
        if (request.platformIds.isEmpty()) {
            throw BadRequestApiException("platformIds must not be empty")
        }

        val actorEmail = currentUserEmail()
        val referenceMonth = parseReferenceMonth(request.referenceMonth)
        val subscriber = loadSubscriber(subscriberId)
        val coveredSubscribers = subscriberRepository
            .findByFinancialResponsibleSubscriberIdAndDeletedAtIsNull(subscriber.id!!)
            .filter { it.id != subscriber.id }
        val billedSubscribers = (listOf(subscriber) + coveredSubscribers)
            .distinctBy { it.id }

        val confirmations = request.platformIds
            .distinct()
            .flatMap { platformId ->
                val targetAssociations = billedSubscribers.mapNotNull { billedSubscriber ->
                    subscriberPlatformRepository.findBySubscriberIdAndPlatformId(billedSubscriber.id!!, platformId)
                        ?.takeIf { it.isActive && it.deletedAt == null && it.platform.deletedAt == null }
                }
                if (targetAssociations.isEmpty()) {
                    throw BadRequestApiException(
                        "No billed subscribers associated with platform id: $platformId"
                    )
                }

                targetAssociations.map { association ->
                    val upserted = upsertConfirmation(
                        actorEmail = actorEmail,
                        referenceMonth = referenceMonth,
                        association = association
                    )
                    toResponse(upserted)
                }
            }
        log.infoEvent("payment.confirmation.bulk-upsert", "entity" to "payment_confirmation", "subscriberId" to subscriber.id, "referenceMonth" to referenceMonth, "count" to confirmations.size, "requestedBy" to actorEmail)
        return confirmations
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
        log.infoEvent("crud.list", "entity" to "payment_confirmation", "status" to PaymentConfirmationStatus.PENDING, "referenceMonth" to referenceMonth, "resultCount" to pending.size)
        return pending.map { toPendingApprovalResponse(it) }
    }

    private fun upsertConfirmation(
        actorEmail: String,
        referenceMonth: YearMonth,
        association: SubscriberPlatform
    ): PaymentConfirmation {
        val now = LocalDateTime.now()
        val existing = paymentConfirmationRepository.findBySubscriberIdAndPlatformIdAndReferenceMonthAndDeletedAtIsNull(
            association.subscriber.id!!,
            association.platform.id!!,
            referenceMonth
        )

        if (existing == null) {
            return paymentConfirmationRepository.save(
                PaymentConfirmation(
                    subscriber = association.subscriber,
                    platform = association.platform,
                    referenceMonth = referenceMonth,
                    status = PaymentConfirmationStatus.CONFIRMED,
                    requestedByEmail = actorEmail,
                    requestedAt = now,
                    validatedByEmail = actorEmail,
                    validatedAt = now,
                    createdAt = now
                )
            )
        }

        if (existing.status == PaymentConfirmationStatus.CONFIRMED) {
            throw BadRequestApiException(
                "Payment already confirmed for subscriberId=${association.subscriber.id}, platformId=${association.platform.id}, referenceMonth=$referenceMonth"
            )
        }

        return paymentConfirmationRepository.save(
            existing.copy(
                status = PaymentConfirmationStatus.CONFIRMED,
                requestedByEmail = actorEmail,
                requestedAt = now,
                validatedByEmail = actorEmail,
                validatedAt = now,
                updatedAt = now
            )
        )
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

    private fun parseReferenceMonth(referenceMonth: String?): YearMonth {
        return if (referenceMonth.isNullOrBlank()) {
            YearMonth.now()
        } else {
            runCatching { YearMonth.parse(referenceMonth) }
                .getOrElse { throw BadRequestApiException("Invalid referenceMonth. Expected format: YYYY-MM") }
        }
    }

    private fun currentUserEmail(): String {
        return SecurityContextHolder.getContext().authentication?.name
            ?.takeIf { it.isNotBlank() }
            ?: throw AccessDeniedException("Unauthenticated user")
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
