package com.aamdigital.aambackendservice.notification.core.create.email

import com.aamdigital.aambackendservice.common.mail.MailSenderRequest
import com.aamdigital.aambackendservice.common.mail.MailSenderService
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationData
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationHandler
import com.aamdigital.aambackendservice.notification.di.NotificationEmailProperties
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import org.slf4j.LoggerFactory
import org.springframework.web.util.HtmlUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.charset.StandardCharsets

class EmailCreateNotificationHandler(
    private val mailSenderService: MailSenderService,
    private val userEmailProvider: UserEmailProvider,
    private val notificationEmailProperties: NotificationEmailProperties,
    private val templateOverridePath: Path = DEFAULT_TEMPLATE_OVERRIDE_PATH
) : CreateNotificationHandler {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val emailBodyTemplate: String = loadEmailBodyTemplate()

    override fun canHandle(notificationChannelType: NotificationChannelType): Boolean =
        NotificationChannelType.EMAIL == notificationChannelType

    override fun createMessage(createUserNotificationEvent: CreateUserNotificationEvent): CreateNotificationData {
        val email = userEmailProvider.lookupEmail(createUserNotificationEvent.userIdentifier)
        if (email == null) {
            logger.info(
                "No email address for user {}, skipping email notification",
                createUserNotificationEvent.userIdentifier
            )
            return CreateNotificationData(success = true, messageCreated = false, messageReference = null)
        }

        val details = createUserNotificationEvent.details
        val subject = "${notificationEmailProperties.subjectPrefix}: ${details.title}"
        val body =
            buildEmailBody(
                title = details.title,
                actionUrl = details.actionUrl,
                manageSettingsUrl = notificationEmailProperties.manageSettingsUrl
            )

        val mailSenderResponse =
            mailSenderService.sendMail(
                MailSenderRequest(
                    to = email,
                    subject = subject,
                    body = body,
                    isHtml = true,
                    headers =
                        mapOf(
                            "List-Unsubscribe" to "<${notificationEmailProperties.manageSettingsUrl}>"
                        )
                )
            )

        return CreateNotificationData(
            success = mailSenderResponse.success,
            messageCreated = mailSenderResponse.success,
            messageReference = mailSenderResponse.messageReference
        )
    }

    private fun buildEmailBody(
        title: String,
        actionUrl: String,
        manageSettingsUrl: String
    ): String =
        emailBodyTemplate
            .replace("{{TITLE}}", HtmlUtils.htmlEscape(title))
            .replace("{{ACTION_URL}}", HtmlUtils.htmlEscape(actionUrl))
            .replace("{{MANAGE_SETTINGS_URL}}", HtmlUtils.htmlEscape(manageSettingsUrl))

    private fun loadEmailBodyTemplate(): String {
        if (Files.isRegularFile(templateOverridePath)) {
            logger.info("Loading notification email template from {}", templateOverridePath)
            return Files.readString(templateOverridePath, StandardCharsets.UTF_8)
        }

        val resource = javaClass.getResourceAsStream(DEFAULT_TEMPLATE_CLASSPATH_PATH)
            ?: throw IllegalStateException(
                "Missing email template resource: $DEFAULT_TEMPLATE_CLASSPATH_PATH"
            )

        logger.info("Loading notification email template from classpath fallback")
        return resource
            .bufferedReader(StandardCharsets.UTF_8)
            .use { it.readText() }
    }

    companion object {
        private const val DEFAULT_TEMPLATE_CLASSPATH_PATH =
            "/notification/create-notification-email-template.html"
        private val DEFAULT_TEMPLATE_OVERRIDE_PATH: Path =
            Paths.get("/opt/app/templates/notification/create-notification-email-template.html")
    }
}
