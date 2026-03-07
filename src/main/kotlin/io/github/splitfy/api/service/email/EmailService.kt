package io.github.splitfy.api.service.email

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
        val message = SimpleMailMessage().apply {
            setFrom(from)
            setTo(to)
            setSubject(subject)
            setText(body)
        }
        executeWithRetry("text", to, subject) {
            mailSender.send(message)
        }
    }

    fun sendHtml(
        to: String,
        subject: String,
        htmlBody: String,
        inlineResources: Map<String, InlineResource> = emptyMap()
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
        executeWithRetry("html", to, subject) {
            mailSender.send(mimeMessage)
        }
    }

    private fun executeWithRetry(kind: String, to: String, subject: String, block: () -> Unit) {
        val attempts = maxAttempts.coerceAtLeast(1)
        for (attempt in 1..attempts) {
            try {
                block()
                return
            } catch (ex: MailException) {
                val isLastAttempt = attempt == attempts
                log.warn(
                    "Failed to send {} email to {} (subject='{}') attempt {}/{}: {}",
                    kind,
                    to,
                    subject,
                    attempt,
                    attempts,
                    ex.message
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
}
