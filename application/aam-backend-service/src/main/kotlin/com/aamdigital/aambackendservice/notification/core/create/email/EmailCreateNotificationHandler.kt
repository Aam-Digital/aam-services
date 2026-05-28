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

class EmailCreateNotificationHandler(
    private val mailSenderService: MailSenderService,
    private val userEmailProvider: UserEmailProvider,
    private val notificationEmailProperties: NotificationEmailProperties
) : CreateNotificationHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

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
        """
        <p>${HtmlUtils.htmlEscape(title)}</p>
        <p><a href="${HtmlUtils.htmlEscape(actionUrl)}">Open in Aam Digital</a></p>
        <hr>
        <p style="font-size:smaller;color:#666">
          You received this email because email notifications are enabled in your Aam Digital account.
          <a href="${HtmlUtils.htmlEscape(manageSettingsUrl)}">Manage notification settings</a>.
        </p>
        """.trimIndent()
}
