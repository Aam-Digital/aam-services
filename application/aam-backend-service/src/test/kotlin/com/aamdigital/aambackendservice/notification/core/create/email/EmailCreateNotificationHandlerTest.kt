package com.aamdigital.aambackendservice.notification.core.create.email

import com.aamdigital.aambackendservice.common.mail.MailSenderRequest
import com.aamdigital.aambackendservice.common.mail.MailSenderResponse
import com.aamdigital.aambackendservice.common.mail.MailSenderService
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.di.NotificationEmailProperties
import com.aamdigital.aambackendservice.notification.domain.EntityNotificationContext
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationDetails
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory

@ExtendWith(MockitoExtension::class)
class EmailCreateNotificationHandlerTest {
    private lateinit var handler: EmailCreateNotificationHandler

    @Mock
    lateinit var mailSenderService: MailSenderService

    @Mock
    lateinit var userEmailProvider: UserEmailProvider

    private val emailProperties =
        NotificationEmailProperties(
            from = "noreply@example.com",
            subjectPrefix = "Aam Digital",
            manageSettingsUrl = "https://app.test/user-account"
        )

    private val notificationEvent =
        CreateUserNotificationEvent(
            userIdentifier = "user-123",
            notificationChannelType = NotificationChannelType.EMAIL,
            notificationRule = "test-rule",
            details =
                NotificationDetails(
                    notificationType = NotificationType.ENTITY_CHANGE,
                    title = "A new record was added",
                    actionUrl = "https://app.test/notification/abc",
                    context = EntityNotificationContext(entityType = "Child", entityId = "Child:1")
                )
        )

    @BeforeEach
    fun setUp() {
        handler =
            EmailCreateNotificationHandler(
                mailSenderService = mailSenderService,
                userEmailProvider = userEmailProvider,
                notificationEmailProperties = emailProperties
            )
    }

    @Test
    fun `canHandle returns true for EMAIL channel type`() {
        assertThat(handler.canHandle(NotificationChannelType.EMAIL)).isTrue()
    }

    @Test
    fun `canHandle returns false for non-EMAIL channel types`() {
        assertThat(handler.canHandle(NotificationChannelType.APP)).isFalse()
        assertThat(handler.canHandle(NotificationChannelType.PUSH)).isFalse()
    }

    @Test
    fun `should send email with correct subject prefix when user has email address`() {
        // Given
        whenever(userEmailProvider.lookupEmail("user-123")).thenReturn("user@example.com")
        whenever(mailSenderService.sendMail(any())).thenReturn(MailSenderResponse(success = true))

        // When
        val result = handler.createMessage(notificationEvent)

        // Then
        assertThat(result.success).isTrue()
        assertThat(result.messageCreated).isTrue()

        val requestCaptor = argumentCaptor<MailSenderRequest>()
        verify(mailSenderService).sendMail(requestCaptor.capture())
        val request = requestCaptor.firstValue
        assertThat(request.to).isEqualTo("user@example.com")
        assertThat(request.subject).startsWith("Aam Digital:")
        assertThat(request.subject).contains("A new record was added")
        assertThat(request.isHtml).isTrue()
    }

    @Test
    fun `should skip sending when user has no email address`() {
        // Given
        val logger = LoggerFactory.getLogger(EmailCreateNotificationHandler::class.java) as LogbackLogger
        val logAppender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(logAppender)
        whenever(userEmailProvider.lookupEmail("user-123")).thenReturn(null)

        // When
        try {
            val result = handler.createMessage(notificationEvent)

            // Then
            assertThat(result.success).isTrue()
            assertThat(result.messageCreated).isFalse()
            verifyNoInteractions(mailSenderService)
        } finally {
            logger.detachAppender(logAppender)
        }

        assertThat(logAppender.list)
            .anySatisfy { event ->
                assertThat(event.level).isEqualTo(Level.INFO)
                assertThat(event.formattedMessage)
                    .isEqualTo("No email address for user user-123, skipping email notification")
            }
    }

    @Test
    fun `should set List-Unsubscribe header in sent email`() {
        // Given
        whenever(userEmailProvider.lookupEmail("user-123")).thenReturn("user@example.com")
        whenever(mailSenderService.sendMail(any())).thenReturn(MailSenderResponse(success = true))

        // When
        handler.createMessage(notificationEvent)

        // Then
        val requestCaptor = argumentCaptor<MailSenderRequest>()
        verify(mailSenderService).sendMail(requestCaptor.capture())
        assertThat(requestCaptor.firstValue.headers).containsKey("List-Unsubscribe")
        assertThat(requestCaptor.firstValue.headers["List-Unsubscribe"])
            .contains("https://app.test/user-account")
    }

    @Test
    fun `should HTML-escape injection attempt in title in both subject and body`() {
        // Given
        val injectionEvent =
            notificationEvent.copy(
                details = notificationEvent.details.copy(title = "<script>alert(1)</script>")
            )
        whenever(userEmailProvider.lookupEmail("user-123")).thenReturn("user@example.com")
        whenever(mailSenderService.sendMail(any())).thenReturn(MailSenderResponse(success = true))

        // When
        handler.createMessage(injectionEvent)

        // Then
        val requestCaptor = argumentCaptor<MailSenderRequest>()
        verify(mailSenderService).sendMail(requestCaptor.capture())
        val request = requestCaptor.firstValue
        assertThat(request.subject).doesNotContain("<script>")
        assertThat(request.body).doesNotContain("<script>")
        assertThat(request.body).contains("&lt;script&gt;")
    }

    @Test
    fun `should include actionUrl as link in email body`() {
        // Given
        whenever(userEmailProvider.lookupEmail("user-123")).thenReturn("user@example.com")
        whenever(mailSenderService.sendMail(any())).thenReturn(MailSenderResponse(success = true))

        // When
        handler.createMessage(notificationEvent)

        // Then
        val requestCaptor = argumentCaptor<MailSenderRequest>()
        verify(mailSenderService).sendMail(requestCaptor.capture())
        assertThat(requestCaptor.firstValue.body).contains("href=\"https://app.test/notification/abc\"")
        assertThat(requestCaptor.firstValue.body).contains("Open in Aam Digital")
    }
}
