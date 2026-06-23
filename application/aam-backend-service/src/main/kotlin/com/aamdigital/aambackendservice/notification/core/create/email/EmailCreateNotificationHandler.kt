package com.aamdigital.aambackendservice.notification.core.create.email

import com.aamdigital.aambackendservice.common.mail.MailSenderRequest
import com.aamdigital.aambackendservice.common.mail.MailSenderService
import com.aamdigital.aambackendservice.common.domain.normalizeUrlWithHttpsDefault
import com.aamdigital.aambackendservice.common.templating.LocalizedTemplateLoader
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationData
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationHandler
import com.aamdigital.aambackendservice.notification.core.create.TransientNotificationException
import com.aamdigital.aambackendservice.notification.di.NotificationEmailProperties
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import org.slf4j.LoggerFactory
import org.springframework.mail.MailSendException
import org.springframework.web.util.HtmlUtils
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.nio.file.Path
import java.nio.file.Paths
import java.io.StringReader
import java.util.Properties

class EmailCreateNotificationHandler(
    private val mailSenderService: MailSenderService,
    private val userEmailProvider: UserEmailProvider,
    private val notificationEmailProperties: NotificationEmailProperties,
    templatesBaseDir: Path = DEFAULT_TEMPLATES_BASE_DIR
) : CreateNotificationHandler {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val templateLoader = LocalizedTemplateLoader(templatesBaseDir)
    private val emailBodyTemplate: String =
        templateLoader.loadRequired(TEMPLATE_RELATIVE_PATH, notificationEmailProperties.locale)
    private val branding: EmailBranding = loadEmailBranding(notificationEmailProperties.locale)
    private val normalizedManageSettingsUrl: String = normalizeUrlWithHttpsDefault(notificationEmailProperties.manageSettingsUrl)
    private val resolvedFrom: String = resolveFrom(notificationEmailProperties.from, branding.fromName)

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
        val subject = "${branding.subjectPrefix}: ${details.title}"
        val body =
            buildEmailBody(
                title = details.title,
                actionUrl = details.actionUrl,
                manageSettingsUrl = normalizedManageSettingsUrl
            )

        val mailSenderResponse =
            try {
                mailSenderService.sendMail(
                    MailSenderRequest(
                        to = email,
                        subject = subject,
                        body = body,
                        from = resolvedFrom,
                        isHtml = true,
                        headers =
                            mapOf(
                                "List-Unsubscribe" to "<$normalizedManageSettingsUrl>"
                            )
                    )
                )
            } catch (ex: MailSendException) {
                if (isTransientMailFailure(ex)) {
                    throw TransientNotificationException("SMTP connection failed: ${ex.localizedMessage}", ex)
                }
                throw ex
            }

        return CreateNotificationData(
            success = mailSenderResponse.success,
            messageCreated = mailSenderResponse.success,
            messageReference = mailSenderResponse.messageReference
        )
    }

    private fun isTransientMailFailure(ex: MailSendException): Boolean {
        val candidates = buildList {
            add(ex as Throwable)
            addAll(ex.failedMessages.values)
        }
        return candidates.any { root ->
            generateSequence(root) { it.cause }
                .any { it is ConnectException || it is SocketTimeoutException }
        }
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

    /**
     * Composes the final sender header from the deployment-specific address
     * ([NotificationEmailProperties.from]) and the [fromName] display text managed in the
     * branding file, e.g. `Aam Digital <notifications@example.org>`.
     *
     * Falls back to the bare configured address when no [fromName] is configured (or the address
     * is empty).
     */
    private fun resolveFrom(configuredFrom: String, fromName: String): String {
        val address = configuredFrom.trim()
        if (address.isEmpty() || fromName.isBlank()) {
            return configuredFrom
        }
        return "$fromName <$address>"
    }

    /**
     * Loads the email branding text (sender display name, subject prefix) for the given locale via
     * the shared [templateLoader]. Missing file or keys fall back to [DEFAULT_SUBJECT_PREFIX] / no
     * display name.
     */
    private fun loadEmailBranding(locale: String): EmailBranding {
        val properties = Properties()
        val content = templateLoader.load(BRANDING_RELATIVE_PATH, locale)
        if (content != null) {
            properties.load(StringReader(content))
        } else {
            logger.warn("No notification email branding file found; using defaults")
        }
        return EmailBranding(
            fromName = properties.getProperty("from-name", "").trim(),
            subjectPrefix = properties.getProperty("subject-prefix", DEFAULT_SUBJECT_PREFIX).trim()
        )
    }

    /**
     * Branding text for notification emails, managed in the `email-branding.properties` template
     * file rather than in deployment config.
     */
    private data class EmailBranding(
        val fromName: String,
        val subjectPrefix: String
    )

    companion object {
        private const val DEFAULT_SUBJECT_PREFIX = "Aam Digital"
        private const val TEMPLATE_RELATIVE_PATH =
            "notification/create-notification-email-template.html"
        private const val BRANDING_RELATIVE_PATH =
            "notification/email-branding.properties"
        private val DEFAULT_TEMPLATES_BASE_DIR: Path =
            Paths.get("/opt/app/templates")
    }
}
