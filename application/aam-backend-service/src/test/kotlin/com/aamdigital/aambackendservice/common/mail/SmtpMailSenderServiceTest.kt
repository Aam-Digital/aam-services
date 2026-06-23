package com.aamdigital.aambackendservice.common.mail

import jakarta.mail.internet.MimeMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSender

@ExtendWith(MockitoExtension::class)
class SmtpMailSenderServiceTest {
    private lateinit var service: SmtpMailSenderService

    @Mock
    lateinit var javaMailSender: JavaMailSender

    @Mock
    lateinit var mimeMessage: MimeMessage

    @BeforeEach
    fun setUp() {
        service = SmtpMailSenderService(javaMailSender)
        whenever(javaMailSender.createMimeMessage()).thenReturn(mimeMessage)
    }

    @Test
    fun `should send mail and return success response`() {
        // Given
        val request =
            MailSenderRequest(
                to = "user@example.com",
                subject = "Test Subject",
                body = "<p>Test</p>",
                isHtml = true
            )

        // When
        val result = service.sendMail(request)

        // Then
        assertThat(result.success).isTrue()
        verify(javaMailSender).send(mimeMessage)
    }

    @Test
    fun `should propagate MailSendException without internal retry`() {
        // Given
        whenever(javaMailSender.send(any<MimeMessage>())).thenThrow(MailSendException("SMTP error"))
        val request =
            MailSenderRequest(
                to = "user@example.com",
                subject = "Subject",
                body = "Body"
            )

        // When / Then
        assertThrows<MailSendException> { service.sendMail(request) }
    }

    @Test
    fun `should set custom headers on the mime message`() {
        // Given
        val request =
            MailSenderRequest(
                to = "user@example.com",
                subject = "Subject",
                body = "Body",
                headers =
                    mapOf(
                        "List-Unsubscribe" to "<https://app.test/unsubscribe>"
                    )
            )

        // When
        service.sendMail(request)

        // Then
        verify(mimeMessage).addHeader("List-Unsubscribe", "<https://app.test/unsubscribe>")
    }
}
