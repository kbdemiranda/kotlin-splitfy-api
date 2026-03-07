package io.github.splitfy.api.service.subscriber

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.splitfy.api.domain.entity.Subscriber
import io.github.splitfy.api.domain.entity.SubscriberPlatform
import io.github.splitfy.api.exception.BadRequestApiException
import io.github.splitfy.api.exception.ConflictApiException
import io.github.splitfy.api.exception.ResourceNotFoundApiException
import io.github.splitfy.api.repository.PlatformRepository
import io.github.splitfy.api.repository.SubscriberPlatformRepository
import io.github.splitfy.api.repository.SubscriberRepository
import io.github.splitfy.api.service.billing.BillingService
import io.github.splitfy.api.service.email.EmailService
import io.github.splitfy.api.service.email.EmailTemplateService
import io.github.splitfy.api.web.billing.dto.BillingResponse
import io.github.splitfy.api.web.subscriber.dto.PlatformAssociationRequest
import io.github.splitfy.api.web.subscriber.dto.SubscriberRequest
import io.github.splitfy.api.web.subscriber.dto.SubscriberResponse
import io.github.splitfy.api.web.billing.dto.SubscriberBillingEmailRequest
import io.github.splitfy.api.web.subscriber.dto.SubscriptionItemResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
@Transactional
class SubscriberService(
    private val subscriberRepository: SubscriberRepository,
    private val platformRepository: PlatformRepository,
    private val subscriberPlatformRepository: SubscriberPlatformRepository,
    private val billingService: BillingService,
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    @Value("\${splitfy.billing.pix-key:123.456.789-00}") private val pixKey: String,
    @Value("\${splitfy.billing.pix-copy-paste:123.456.789-00}") private val pixCopyPaste: String,
) {

    private val finalScale = 2
    private val rounding = RoundingMode.HALF_UP

    fun create(dto: SubscriberRequest): SubscriberResponse {
        val entity = Subscriber(
            subscriberToken = UUID.randomUUID(),
            name = dto.name,
            email = dto.email,
            createdAt = LocalDateTime.now(),
        )
        val saved = subscriberRepository.save(entity)
        return toDto(saved)
    }

    fun list(pageable: Pageable, name: String?): Page<SubscriberResponse> {
        val subscribers = if (name.isNullOrBlank()) {
            subscriberRepository.findByDeletedAtIsNull(pageable)
        } else {
            subscriberRepository.findByDeletedAtIsNullAndNameContainingIgnoreCase(name, pageable)
        }
        return subscribers.map { toDto(it, includeSubscriptions = false) }
    }

    fun get(id: Long): SubscriberResponse? {
        val subscriber = getSubscriber(id)
        return toDto(subscriber, includeSubscriptions = true)
    }

    fun getSubscriptions(id: Long): List<SubscriptionItemResponse> {
        val subscriber = getSubscriber(id)
        return toDto(subscriber, includeSubscriptions = true).subscriptions
    }

    @PreAuthorize("hasRole('ADMIN') or (hasRole('EDITOR') and @subscriberSecurity.isOwner(#id, authentication.name))")
    fun update(id: Long, dto: SubscriberRequest): SubscriberResponse? {
        val updated = getSubscriber(id).copy(
            name = dto.name,
            email = dto.email,
            updatedAt = LocalDateTime.now()
        )
        val saved = subscriberRepository.save(updated)
        return toDto(saved)
    }

    fun delete(id: Long) {
        val subscriber = getSubscriber(id).copy(deletedAt = LocalDateTime.now())
        subscriberRepository.save(subscriber)
    }

    private fun getSubscriber(id: Long): Subscriber {
        return subscriberRepository.findById(id)
            .orElseThrow { ResourceNotFoundApiException("Subscriber not found with id: $id") }
    }

    private fun toDto(entity: Subscriber, includeSubscriptions: Boolean = false): SubscriberResponse {
        val subscriptions = if (includeSubscriptions) {
            entity.id?.let { subscriberId ->
                val associations = subscriberPlatformRepository.findActiveBySubscriberIdWithPlatform(subscriberId)
                if (associations.isEmpty()) {
                    emptyList()
                } else {
                    val platformIds = associations.mapNotNull { it.platform.id }
                    val participantsByPlatform = subscriberPlatformRepository
                        .countActiveParticipantsByPlatformIds(platformIds)
                        .associate { it.getPlatformId() to it.getCount() }

                    associations.map { association ->
                        val platformId = association.platform.id
                        val participantsCount = platformId?.let { participantsByPlatform[it] } ?: 1L
                        val splitPrice = if (participantsCount > 0L) {
                            association.platform.price
                                .divide(BigDecimal(participantsCount), 10, rounding)
                                .setScale(finalScale, rounding)
                        } else {
                            association.platform.price.setScale(finalScale, rounding)
                        }

                        SubscriptionItemResponse(
                            platformId = platformId,
                            platformName = association.platform.name,
                            serviceType = association.platform.serviceType,
                        )
                    }
                }
            } ?: emptyList()
        } else {
            emptyList()
        }

        return SubscriberResponse(
            id = entity.id,
            name = entity.name,
            email = entity.email,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            deletedAt = entity.deletedAt,
            subscriptions = subscriptions
        )
    }

    @PreAuthorize("hasRole('ADMIN') or (hasRole('EDITOR') and @subscriberSecurity.isOwner(#id, authentication.name))")
    fun associatePlatforms(id: Long, platformAssociationRequest: List<PlatformAssociationRequest>) {
        if (platformAssociationRequest.isEmpty()) return

        val platformIds = platformAssociationRequest.flatMap { it.platformIds }.toSet()
        if (platformIds.isEmpty()) return

        val subscriber = getSubscriber(id)

        for (platformId in platformIds) {
            val platform = platformRepository.findById(platformId)
                .orElseThrow { ResourceNotFoundApiException("Platform not found with id: $platformId") }

            if (platform.deletedAt != null) {
                throw ResourceNotFoundApiException("Platform not found with id: $platformId")
            }

            if (platform.availableSlots <= 0) {
                throw BadRequestApiException("Plataforma sem vagas disponíveis")
            }

            val existingAssociation = subscriber.id?.let {
                subscriberPlatformRepository.findBySubscriberIdAndPlatformId(it, platformId)
            }

            when {
                existingAssociation == null -> {
                    val association = SubscriberPlatform(
                        subscriber = subscriber,
                        platform = platform,
                        createdAt = LocalDateTime.now()
                    )
                    subscriberPlatformRepository.save(association)
                }
                existingAssociation.isActive && existingAssociation.deletedAt == null -> {
                    throw ConflictApiException("Subscriber already associated with platform id: $platformId")
                }
                else -> {
                    val reactivatedAssociation = existingAssociation.copy(
                        isActive = true,
                        unsubscribedAt = null,
                        updatedAt = LocalDateTime.now(),
                        deletedAt = null
                    )
                    subscriberPlatformRepository.save(reactivatedAssociation)
                }
            }

            val updatedPlatform = platform.copy(
                availableSlots = platform.availableSlots - 1,
                updatedAt = LocalDateTime.now()
            )
            platformRepository.save(updatedPlatform)
        }
    }

    @PreAuthorize("hasRole('ADMIN') or (hasRole('EDITOR') and @subscriberSecurity.isOwner(#id, authentication.name))")
    fun disassociatePlatforms(id: Long, platformAssociationRequest: List<PlatformAssociationRequest>) {
        if (platformAssociationRequest.isEmpty()) return

        val platformIds = platformAssociationRequest.flatMap { it.platformIds }.toSet()
        if (platformIds.isEmpty()) return

        val subscriber = getSubscriber(id)

        for (platformId in platformIds) {
            val platform = platformRepository.findById(platformId)
                .orElseThrow { ResourceNotFoundApiException("Platform not found with id: $platformId") }

            if (platform.deletedAt != null) {
                throw ResourceNotFoundApiException("Platform not found with id: $platformId")
            }

            val association = subscriber.id?.let {
                subscriberPlatformRepository.findBySubscriberIdAndPlatformId(it, platformId)
            }

            if (association == null || !association.isActive || association.deletedAt != null) {
                throw BadRequestApiException("Subscriber not associated with platform id: $platformId")
            }

            val updatedAssociation = association.copy(
                isActive = false,
                unsubscribedAt = LocalDateTime.now(),
                deletedAt = LocalDateTime.now()
            )
            subscriberPlatformRepository.save(updatedAssociation)

            val updatedPlatform = platform.copy(
                availableSlots = platform.availableSlots + 1,
                updatedAt = LocalDateTime.now()
            )
            platformRepository.save(updatedPlatform)
        }
    }

    fun sendBillingSummaryToEmails(request: SubscriberBillingEmailRequest) {
        val subscriberIds = request.subscriberIds.distinct()
        if (subscriberIds.isEmpty()) {
            throw BadRequestApiException("subscriberIds must not be empty")
        }

        val emails = request.emails.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (emails.isEmpty()) {
            throw BadRequestApiException("emails must not be empty")
        }

        val referenceMonth = parseReferenceMonthOrNow(request.referenceMonth)

        val subscribersById = subscriberRepository.findAllById(subscriberIds)
            .associateBy { it.id }

        val missingSubscriberIds = subscriberIds.filter { subscribersById[it] == null }
        if (missingSubscriberIds.isNotEmpty()) {
            throw ResourceNotFoundApiException("Subscribers not found with ids: ${missingSubscriberIds.joinToString(", ")}")
        }

        val deletedSubscriberIds = subscribersById.values
            .filter { it.deletedAt != null }
            .mapNotNull { it.id }
        if (deletedSubscriberIds.isNotEmpty()) {
            throw ResourceNotFoundApiException("Subscribers not found with ids: ${deletedSubscriberIds.joinToString(", ")}")
        }

        val billingBySubscriber = subscriberIds.map { subscriberId ->
            val subscriber = subscribersById[subscriberId]!!
            Pair(subscriber, billingService.getBillingForSubscriber(subscriberId, referenceMonth))
        }

        val subject = "Resumo de cobranças Splitfy - $referenceMonth"
        val htmlBody = buildBillingSummaryHtml(billingBySubscriber, referenceMonth)
        val inlineResources = mapOf(
            PIX_QR_CODE_CONTENT_ID to EmailService.InlineResource(
                source = ByteArrayResource(generatePixQrCodePng(pixCopyPaste)),
                contentType = "image/png"
            )
        )

        emails.forEach { email ->
            emailService.sendHtml(email, subject, htmlBody, inlineResources)
        }
    }

    private fun parseReferenceMonthOrNow(referenceMonth: String?): YearMonth {
        if (referenceMonth.isNullOrBlank()) {
            return YearMonth.now()
        }

        return runCatching { YearMonth.parse(referenceMonth.trim()) }
            .getOrElse { throw BadRequestApiException("Invalid referenceMonth. Expected format: YYYY-MM") }
    }

    private fun buildBillingSummaryHtml(
        billingBySubscriber: List<Pair<Subscriber, BillingResponse>>,
        referenceMonth: YearMonth
    ): String {
        val referenceLabel = referenceMonth.format(REFERENCE_MONTH_FORMATTER)
        var grandTotal = BigDecimal.ZERO.setScale(finalScale, rounding)
        val subscribers = billingBySubscriber.map { (subscriber, billing) ->
            grandTotal = grandTotal.add(billing.totalMonthlyDue).setScale(finalScale, rounding)

            BillingSummarySubscriber(
                name = subscriber.name,
                email = subscriber.email,
                totalDue = formatCurrency(billing.totalMonthlyDue),
                services = billing.items.map { item ->
                    BillingSummaryService(
                        name = item.serviceName,
                        platformValue = formatCurrency(item.serviceMonthlyAmount),
                        subscriberShare = formatCurrency(item.userMonthlyShare),
                        participantsCount = item.participantsCount
                    )
                }
            )
        }

        return emailTemplateService.renderTemplate(
            templateName = "email/billing-summary",
            variables = mapOf(
                "preheader" to "Resumo de cobrancas Splitfy - $referenceLabel",
                "referenceMonth" to referenceLabel,
                "subscriberCount" to subscribers.size,
                "subscribers" to subscribers,
                "grandTotal" to formatCurrency(grandTotal),
                "pixKey" to pixKey,
                "pixQrCodeCid" to PIX_QR_CODE_CONTENT_ID
            )
        )
    }

    private fun formatCurrency(value: BigDecimal): String {
        return "R$ ${value.setScale(finalScale, rounding).toPlainString()}"
    }

    private fun generatePixQrCodePng(content: String): ByteArray {
        val normalizedContent = content.trim().ifBlank { pixKey }
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
        )
        val matrix = QRCodeWriter().encode(normalizedContent, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE, hints)
        val image = BufferedImage(matrix.width, matrix.height, BufferedImage.TYPE_INT_RGB)

        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                image.setRGB(x, y, if (matrix.get(x, y)) Color.BLACK.rgb else Color.WHITE.rgb)
            }
        }

        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "PNG", output)
            output.toByteArray()
        }
    }

    private data class BillingSummarySubscriber(
        val name: String,
        val email: String,
        val totalDue: String,
        val services: List<BillingSummaryService>
    )

    private data class BillingSummaryService(
        val name: String,
        val platformValue: String,
        val subscriberShare: String,
        val participantsCount: Int
    )

    companion object {
        private val REFERENCE_MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/yyyy")
        private const val PIX_QR_CODE_CONTENT_ID = "pixQrCode"
        private const val QR_CODE_SIZE = 256
    }
}
