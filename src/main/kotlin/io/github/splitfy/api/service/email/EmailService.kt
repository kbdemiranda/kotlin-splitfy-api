package io.github.splitfy.api.service.email

import io.github.splitfy.api.logging.EmailLogContext
import io.github.splitfy.api.logging.infoEvent
import io.github.splitfy.api.logging.warnEvent
import org.springframework.core.io.InputStreamSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    @Value("\${splitfy.mail.from}") private val from: String,
    @Value("\${splitfy.mail.retry.max-attempts:3}") private val maxAttempts: Int,
    @Value("\${splitfy.mail.retry.backoff-ms:500}") private val backoffMs: Long,
) {

    private val log = LoggerFactory.getLogger(EmailService::class.java)

    fun send(to: String, subject: String, body: String) {
        send(to, subject, body, null)
    }

    fun send(to: String, subject: String, body: String, context: EmailLogContext? = null) {
        val message = SimpleMailMessage().apply {
            setFrom(from)
            setTo(to)
            setSubject(subject)
            setText(body)
        }
        executeWithRetry("text", to, subject, context) {
            mailSender.send(message)
        }
    }

    fun sendHtml(
        to: String,
        subject: String,
        htmlBody: String,
        inlineResources: Map<String, InlineResource> = emptyMap()
    ) {
        sendHtml(to, subject, htmlBody, inlineResources, null)
    }

    fun sendHtml(
        to: String,
        subject: String,
        htmlBody: String,
        inlineResources: Map<String, InlineResource> = emptyMap(),
        context: EmailLogContext? = null,
    ) {
        val mimeMessage = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(mimeMessage, inlineResources.isNotEmpty(), "UTF-8")
        helper.setFrom(from)
        helper.setTo(to)
        helper.setSubject(subject)
        helper.setText(htmlBody, true)
        inlineResources.forEach { (contentId, resource) ->
            helper.addInline(contentId, resource.source, resource.contentType)
        }
        executeWithRetry("html", to, subject, context) {
            mailSender.send(mimeMessage)
        }
    }

    private fun executeWithRetry(kind: String, to: String, subject: String, context: EmailLogContext?, block: () -> Unit) {
        val attempts = maxAttempts.coerceAtLeast(1)
        for (attempt in 1..attempts) {
            try {
                block()
                log.infoEvent(
                    context?.event ?: "email.sent",
                    "emailKind" to kind,
                    "recipient" to to,
                    "subject" to subject,
                    "attempt" to attempt,
                    "entity" to context?.entity,
                    "entityId" to context?.entityId,
                    "entityToken" to context?.entityToken,
                    *context.toMetadataFields(),
                )
                return
            } catch (ex: MailException) {
                val isLastAttempt = attempt == attempts
                log.warnEvent(
                    context?.event?.plus(".failed") ?: "email.send.failed",
                    ex,
                    "emailKind" to kind,
                    "recipient" to to,
                    "subject" to subject,
                    "attempt" to attempt,
                    "maxAttempts" to attempts,
                    "entity" to context?.entity,
                    "entityId" to context?.entityId,
                    "entityToken" to context?.entityToken,
                    *context.toMetadataFields(),
                )
                if (isLastAttempt) throw ex
                sleepBackoff(attempt)
            }
        }
    }

    private fun sleepBackoff(attempt: Int) {
        val delay = backoffMs.coerceAtLeast(0) * attempt
        if (delay <= 0L) return
        try {
            Thread.sleep(delay)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    data class InlineResource(
        val source: InputStreamSource,
        val contentType: String
    )

    private fun EmailLogContext?.toMetadataFields(): Array<Pair<String, Any?>> {
        if (this == null || metadata.isEmpty()) {
            return emptyArray()
        }
        return metadata.entries.map { it.key to it.value }.toTypedArray()
    }
}
