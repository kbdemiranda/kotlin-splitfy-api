package io.github.splitfy.api.service.email

import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    @Value("\${splitfy.mail.from}") private val from: String
) {

    fun send(to: String, subject: String, body: String) {
        val message = SimpleMailMessage().apply {
            setFrom(from)
            setTo(to)
            setSubject(subject)
            setText(body)
        }
        mailSender.send(message)
    }
}
