package io.github.splitfy.api.service.email

import io.github.splitfy.api.logging.EmailLogContext
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.io.ByteArrayResource
import org.springframework.mail.MailSendException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import java.util.Properties

@ExtendWith(MockitoExtension::class)
class EmailServiceTest {

    @Mock
    lateinit var mailSender: JavaMailSender

    @Test
    fun `send builds simple text message and delegates to mail sender`() {
        val service = EmailService(mailSender, "no-reply@splitfy.local", 3, 0)
        val context = EmailLogContext(
            event = "billing.email.sent",
            entity = "subscriber",
            entityId = 10L,
            entityToken = "token-10",
            metadata = mapOf("referenceMonth" to "2026-04")
        )

        service.send("user@example.com", "Subject", "Body", context)

        val captor = argumentCaptor<SimpleMailMessage>()
        verify(mailSender).send(captor.capture())
        assertEquals("user@example.com", captor.firstValue.to?.single())
        assertEquals("Subject", captor.firstValue.subject)
        assertEquals("Body", captor.firstValue.text)
    }

    @Test
    fun `sendHtml builds mime message with inline resources`() {
        val mimeMessage = MimeMessage(Session.getInstance(Properties()))
        whenever(mailSender.createMimeMessage()).thenReturn(mimeMessage)
        val service = EmailService(mailSender, "no-reply@splitfy.local", 1, 0)

        service.sendHtml(
            to = "user@example.com",
            subject = "HTML",
            htmlBody = "<p>Hello</p>",
            inlineResources = mapOf(
                "logo" to EmailService.InlineResource(
                    source = ByteArrayResource("png".toByteArray()),
                    contentType = "image/png"
                )
            ),
            context = EmailLogContext("email.html.sent", "user")
        )

        verify(mailSender).send(mimeMessage)
    }

    @Test
    fun `send retries transient failures and succeeds before exhaustion`() {
        val service = EmailService(mailSender, "no-reply@splitfy.local", 2, 0)
        doThrow(MailSendException("smtp down"))
            .doNothing()
            .whenever(mailSender)
            .send(any<SimpleMailMessage>())

        service.send("user@example.com", "Subject", "Body")

        verify(mailSender, times(2)).send(any<SimpleMailMessage>())
    }

    @Test
    fun `send throws last mail exception when retries are exhausted`() {
        val service = EmailService(mailSender, "no-reply@splitfy.local", 2, 0)
        doThrow(MailSendException("smtp down"))
            .whenever(mailSender)
            .send(any<SimpleMailMessage>())

        assertThrows(MailSendException::class.java) {
            service.send("user@example.com", "Subject", "Body")
        }

        verify(mailSender, times(2)).send(any<SimpleMailMessage>())
    }

    @Test
    fun `send coerces max attempts below one to a single attempt`() {
        val service = EmailService(mailSender, "no-reply@splitfy.local", 0, 0)
        doThrow(MailSendException("smtp down"))
            .whenever(mailSender)
            .send(any<SimpleMailMessage>())

        assertThrows(MailSendException::class.java) {
            service.send("user@example.com", "Subject", "Body")
        }

        verify(mailSender, times(1)).send(any<SimpleMailMessage>())
    }

    @Test
    fun `sendHtml retries mime message failures`() {
        val mimeMessage = MimeMessage(Session.getInstance(Properties()))
        whenever(mailSender.createMimeMessage()).thenReturn(mimeMessage)
        val service = EmailService(mailSender, "no-reply@splitfy.local", 2, 0)
        doThrow(MailSendException("smtp down"))
            .doNothing()
            .whenever(mailSender)
            .send(mimeMessage)

        service.sendHtml("user@example.com", "Subject", "<p>Body</p>")

        verify(mailSender, times(2)).send(mimeMessage)
    }
}
