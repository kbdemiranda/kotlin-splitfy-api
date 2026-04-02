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
import io.github.splitfy.api.logging.infoEvent
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
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(SubscriberService::class.java)

    private val finalScale = 2
    private val rounding = RoundingMode.HALF_UP

    fun create(dto: SubscriberRequest): SubscriberResponse {
        val saved = subscriberRepository.save(
            Subscriber(
                subscriberToken = UUID.randomUUID(),
                name = dto.name,
                email = dto.email,
                createdAt = LocalDateTime.now(),
            )
        )
        val responsible = resolveResponsibleForCreate(saved, dto.financialResponsibleSubscriberId)
        val finalSubscriber = if (saved.financialResponsibleSubscriber?.id == responsible.id) {
            saved
        } else {
            subscriberRepository.save(
                saved.copy(
                    financialResponsibleSubscriber = responsible,
                    updatedAt = LocalDateTime.now()
                )
            )
        }
        log.infoEvent("crud.create", "entity" to "subscriber", "entityId" to finalSubscriber.id, "entityToken" to finalSubscriber.subscriberToken, "email" to finalSubscriber.email)
        return toDto(finalSubscriber)
    }

    fun list(pageable: Pageable, name: String?): Page<SubscriberResponse> {
        val subscribers = if (name.isNullOrBlank()) {
            subscriberRepository.findByDeletedAtIsNull(pageable)
        } else {
            subscriberRepository.findByDeletedAtIsNullAndNameContainingIgnoreCase(name, pageable)
        }
        log.infoEvent("crud.list", "entity" to "subscriber", "page" to pageable.pageNumber, "size" to pageable.pageSize, "filterName" to name, "resultCount" to subscribers.numberOfElements)
        return subscribers.map { toDto(it, includeSubscriptions = false) }
    }

    fun get(id: Long): SubscriberResponse? {
        val subscriber = getSubscriber(id)
        log.infoEvent("crud.get", "entity" to "subscriber", "entityId" to subscriber.id, "entityToken" to subscriber.subscriberToken, "email" to subscriber.email)
        return toDto(subscriber, includeSubscriptions = true)
    }

    fun getSubscriptions(id: Long): List<SubscriptionItemResponse> {
        val subscriber = getSubscriber(id)
        val subscriptions = toDto(subscriber, includeSubscriptions = true).subscriptions
        log.infoEvent("subscriber.subscriptions.list", "entity" to "subscriber", "entityId" to subscriber.id, "entityToken" to subscriber.subscriberToken, "subscriptionCount" to subscriptions.size)
        return subscriptions
    }

    @PreAuthorize("hasRole('ADMIN') or (hasRole('EDITOR') and @subscriberSecurity.isOwner(#id, authentication.name))")
    fun update(id: Long, dto: SubscriberRequest): SubscriberResponse? {
        val updated = getSubscriber(id).copy(
            name = dto.name,
            email = dto.email,
            financialResponsibleSubscriber = resolveResponsible(dto.financialResponsibleSubscriberId ?: id),
            updatedAt = LocalDateTime.now()
        )
        val saved = subscriberRepository.save(updated)
        log.infoEvent("crud.update", "entity" to "subscriber", "entityId" to saved.id, "entityToken" to saved.subscriberToken, "email" to saved.email)
        return toDto(saved)
    }

    fun delete(id: Long) {
        val subscriber = getSubscriber(id).copy(deletedAt = LocalDateTime.now())
        val saved = subscriberRepository.save(subscriber)
        log.infoEvent("crud.delete", "entity" to "subscriber", "entityId" to saved.id, "entityToken" to saved.subscriberToken, "email" to saved.email)
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

        val responsible = entity.financialResponsibleSubscriber ?: entity
        return SubscriberResponse(
            id = entity.id,
            name = entity.name,
            email = entity.email,
            financialResponsibleSubscriberId = responsible.id,
            financialResponsibleSubscriberName = responsible.name,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            deletedAt = entity.deletedAt,
            subscriptions = subscriptions
        )
    }

    private fun resolveResponsibleForCreate(saved: Subscriber, requestedResponsibleId: Long?): Subscriber {
        if (requestedResponsibleId == null || requestedResponsibleId == saved.id) {
            return saved
        }
        return resolveResponsible(requestedResponsibleId)
    }

    private fun resolveResponsible(responsibleId: Long): Subscriber {
        val responsible = subscriberRepository.findById(responsibleId)
            .orElseThrow { ResourceNotFoundApiException("Financial responsible subscriber not found with id: $responsibleId") }
        if (responsible.deletedAt != null) {
            throw ResourceNotFoundApiException("Financial responsible subscriber not found with id: $responsibleId")
        }
        return responsible
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
        log.infoEvent("subscriber.platforms.associated", "entity" to "subscriber", "entityId" to subscriber.id, "entityToken" to subscriber.subscriberToken, "platformIds" to platformIds.joinToString(","))
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
        log.infoEvent("subscriber.platforms.disassociated", "entity" to "subscriber", "entityId" to subscriber.id, "entityToken" to subscriber.subscriberToken, "platformIds" to platformIds.joinToString(","))
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

        val expandedSubscribers = expandSubscribersForBilling(
            selectedSubscriberIds = subscriberIds,
            selectedSubscribersById = subscribersById
        )

        val billingBySubscriber = expandedSubscribers.map { subscriber ->
            Pair(subscriber, billingService.getBillingForSubscriber(subscriber.id!!, referenceMonth))
        }

        val subject = "Resumo de cobranças Splitfy - $referenceMonth"
        val inlineResources = mapOf(
            PIX_QR_CODE_CONTENT_ID to EmailService.InlineResource(
                source = ByteArrayResource(generatePixQrCodePng(pixCopyPaste)),
                contentType = "image/png"
            )
        )

        emails.forEach { email ->
            val responsibleContext = buildResponsibleContextForRecipient(
                recipientEmail = email,
                subscribers = billingBySubscriber.map { it.first }
            )
            val htmlBody = buildBillingSummaryHtml(
                billingBySubscriber = billingBySubscriber,
                referenceMonth = referenceMonth,
                responsibleContext = responsibleContext
            )
            emailService.sendHtml(
                to = email,
                subject = subject,
                htmlBody = htmlBody,
                inlineResources = inlineResources,
            )
            log.infoEvent("email.billing.summary.sent", "entity" to "subscriber_batch", "subscriberIds" to subscriberIds.joinToString(","), "referenceMonth" to referenceMonth, "recipient" to email)
        }
        log.infoEvent("subscriber.billing-summary.dispatched", "entity" to "subscriber_batch", "subscriberIds" to subscriberIds.joinToString(","), "recipientCount" to emails.size, "referenceMonth" to referenceMonth)
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
        referenceMonth: YearMonth,
        responsibleContext: BillingSummaryResponsibleContext?
    ): String {
        val referenceLabel = referenceMonth.format(REFERENCE_MONTH_FORMATTER)
        val visibleBillingBySubscriber = reduceDuplicatedResponsibleSummaries(billingBySubscriber)
        val subscriberCount = responsibleContext?.coveredSubscribers?.size ?: visibleBillingBySubscriber.size
        var grandTotal = BigDecimal.ZERO.setScale(finalScale, rounding)
        val subscribers = visibleBillingBySubscriber.map { (subscriber, billing) ->
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
                "subscriberCount" to subscriberCount,
                "subscribers" to subscribers,
                "grandTotal" to formatCurrency(grandTotal),
                "responsibleContext" to responsibleContext,
                "pixKey" to pixKey,
                "pixQrCodeCid" to PIX_QR_CODE_CONTENT_ID
            )
        )
    }

    private fun reduceDuplicatedResponsibleSummaries(
        billingBySubscriber: List<Pair<Subscriber, BillingResponse>>
    ): List<Pair<Subscriber, BillingResponse>> {
        val groups = billingBySubscriber.groupBy { (subscriber, _) ->
            (subscriber.financialResponsibleSubscriber ?: subscriber).id
        }
        val replacedGroups = mutableSetOf<Long>()
        val reduced = mutableListOf<Pair<Subscriber, BillingResponse>>()

        billingBySubscriber.forEach { pair ->
            val (subscriber, _) = pair
            val responsibleId = (subscriber.financialResponsibleSubscriber ?: subscriber).id
            if (responsibleId == null) {
                reduced += pair
                return@forEach
            }

            val group = groups[responsibleId].orEmpty()
            val responsiblePair = group.firstOrNull { (groupSubscriber, _) -> groupSubscriber.id == responsibleId }
            val hasConsolidatedResponsible = group.size > 1 && responsiblePair != null

            if (!hasConsolidatedResponsible) {
                reduced += pair
                return@forEach
            }

            if (replacedGroups.add(responsibleId)) {
                reduced += responsiblePair!!
            }
        }

        return reduced
    }

    private fun buildResponsibleContextForRecipient(
        recipientEmail: String,
        subscribers: List<Subscriber>
    ): BillingSummaryResponsibleContext? {
        val normalizedRecipient = recipientEmail.trim().lowercase()
        if (normalizedRecipient.isBlank()) return null

        val coveredSubscribers = subscribers.filter { subscriber ->
            val responsible = subscriber.financialResponsibleSubscriber ?: subscriber
            responsible.email.trim().lowercase() == normalizedRecipient
        }
        if (coveredSubscribers.size <= 1) return null

        val responsibleName = (coveredSubscribers.first().financialResponsibleSubscriber ?: coveredSubscribers.first()).name
        return BillingSummaryResponsibleContext(
            responsibleName = responsibleName,
            coveredSubscribers = coveredSubscribers.map { it.name }
        )
    }

    private fun expandSubscribersForBilling(
        selectedSubscriberIds: List<Long>,
        selectedSubscribersById: Map<Long?, Subscriber>
    ): List<Subscriber> {
        val selectedSubscribers = selectedSubscriberIds.map { selectedSubscribersById[it]!! }
        val expandedById = linkedMapOf<Long, Subscriber>()

        selectedSubscribers.forEach { subscriber ->
            expandedById[subscriber.id!!] = subscriber
        }

        val responsibleIds = selectedSubscribers.mapNotNull { subscriber ->
            (subscriber.financialResponsibleSubscriber ?: subscriber).id
        }.distinct()

        responsibleIds.forEach { responsibleId ->
            val covered = subscriberRepository.findByFinancialResponsibleSubscriberIdAndDeletedAtIsNull(responsibleId)
                .sortedBy { it.id ?: Long.MAX_VALUE }
            covered.forEach { coveredSubscriber ->
                expandedById[coveredSubscriber.id!!] = coveredSubscriber
            }
        }

        return expandedById.values.toList()
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

    private data class BillingSummaryResponsibleContext(
        val responsibleName: String,
        val coveredSubscribers: List<String>
    )

    companion object {
        private val REFERENCE_MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/yyyy")
        private const val PIX_QR_CODE_CONTENT_ID = "pixQrCode"
        private const val QR_CODE_SIZE = 256
    }
}
