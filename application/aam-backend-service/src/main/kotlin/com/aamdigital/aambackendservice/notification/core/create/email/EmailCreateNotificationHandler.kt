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
import java.nio.charset.StandardCharsets

class EmailCreateNotificationHandler(
    private val mailSenderService: MailSenderService,
    private val userEmailProvider: UserEmailProvider,
    private val notificationEmailProperties: NotificationEmailProperties
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
        val subject = "${notificationEmailProperties.subjectPrefix}: ${HtmlUtils.htmlEscape(details.title)}"
        val body =
            buildEmailBody(
                title = details.title,
                actionUrl = details.actionUrl,
                manageSettingsUrl = notificationEmailProperties.manageSettingsUrl
            )

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

        return CreateNotificationData(success = true, messageCreated = true, messageReference = null)
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
                val templatePath = "/notification/email/create-notification-email-template.html"
                val resource = javaClass.getResourceAsStream(templatePath)
                        ?: throw IllegalStateException("Missing email template resource: $templatePath")

                return resource
                        .bufferedReader(StandardCharsets.UTF_8)
                        .use { it.readText() }
        }
}
