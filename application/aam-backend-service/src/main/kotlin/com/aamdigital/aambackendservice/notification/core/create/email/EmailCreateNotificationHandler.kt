package com.aamdigital.aambackendservice.notification.core.create.email

import com.aamdigital.aambackendservice.common.domain.normalizeUrlWithHttpsDefault
import com.aamdigital.aambackendservice.common.keycloak.core.UserProfileLookupException
import com.aamdigital.aambackendservice.common.keycloak.core.UserProfileProvider
import com.aamdigital.aambackendservice.common.mail.MailSenderRequest
import com.aamdigital.aambackendservice.common.mail.MailSenderService
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
import java.io.StringReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

class EmailCreateNotificationHandler(
    private val mailSenderService: MailSenderService,
    private val userProfileProvider: UserProfileProvider,
    private val notificationEmailProperties: NotificationEmailProperties,
    templatesBaseDir: Path = DEFAULT_TEMPLATES_BASE_DIR
) : CreateNotificationHandler {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val templateLoader = LocalizedTemplateLoader(templatesBaseDir)
    private val emailBodyTemplate: String =
        templateLoader.loadRequired(TEMPLATE_RELATIVE_PATH, notificationEmailProperties.locale)
    private val publicFormBodyTemplate: String =
        templateLoader.loadRequired(PUBLIC_FORM_TEMPLATE_RELATIVE_PATH, notificationEmailProperties.locale)
    private val branding: EmailBranding = loadEmailBranding(notificationEmailProperties.locale)
    private val normalizedManageSettingsUrl: String =
        normalizeUrlWithHttpsDefault(notificationEmailProperties.manageSettingsUrl)
    private val resolvedFrom: String = resolveFrom(notificationEmailProperties.from, branding.fromName)

    override fun canHandle(notificationChannelType: NotificationChannelType): Boolean =
        NotificationChannelType.EMAIL == notificationChannelType

    override fun createMessage(createUserNotificationEvent: CreateUserNotificationEvent): CreateNotificationData {
        // a recipient without a user account carries the address on the event; both the address and
        // which template to use come from this one value, so they cannot disagree
        val externalEmail = createUserNotificationEvent.recipientEmail?.takeIf { it.isNotBlank() }
        val email = externalEmail ?: lookupEmail(createUserNotificationEvent.userIdentifier)
        if (email == null) {
            logger.info(
                "No email address for user {}, skipping email notification",
                createUserNotificationEvent.userIdentifier
            )
            return CreateNotificationData(success = true, messageCreated = false, messageReference = null)
        }

        val details = createUserNotificationEvent.details

        // without an account there is nothing to open in the app and no settings to manage: no links, no unsubscribe
        val isExternalRecipient = externalEmail != null
        // the form's admin may have written subject and message; otherwise the default wording comes
        // from the branding file, so that it is translated with the rest of the email
        val customSubject = createUserNotificationEvent.customSubject?.takeIf { it.isNotBlank() }
        val subject =
            if (isExternalRecipient) {
                val parts =
                    customSubject?.let { listOf(branding.subjectPrefix, it) }
                        ?: listOf(branding.subjectPrefix, branding.submissionConfirmationLabel, details.title)
                parts.filter { it.isNotBlank() }.joinToString(": ")
            } else {
                "${branding.subjectPrefix}: ${details.title}"
            }
        val body =
            if (isExternalRecipient) {
                buildPublicFormEmailBody(
                    title = customSubject ?: details.title.ifBlank { branding.submissionConfirmationLabel },
                    message = createUserNotificationEvent.customBody?.takeIf { it.isNotBlank() }
                )
            } else {
                buildEmailBody(
                    title = details.title,
                    actionUrl = details.actionUrl,
                    manageSettingsUrl = normalizedManageSettingsUrl
                )
            }
        val headers =
            if (isExternalRecipient) {
                emptyMap()
            } else {
                mapOf("List-Unsubscribe" to "<$normalizedManageSettingsUrl>")
            }

        val mailSenderResponse =
            try {
                mailSenderService.sendMail(
                    MailSenderRequest(
                        to = email,
                        subject = subject,
                        body = body,
                        from = resolvedFrom,
                        isHtml = true,
                        headers = headers
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

    /** The user's email address, or `null` if the user has none (or does not exist). */
    private fun lookupEmail(userIdentifier: String): String? =
        try {
            userProfileProvider.getUserProfile(userIdentifier)?.email?.takeIf { it.isNotBlank() }
        } catch (ex: UserProfileLookupException) {
            throw TransientNotificationException("Email address lookup failed: ${ex.localizedMessage}", ex)
        }

    private fun isTransientMailFailure(ex: MailSendException): Boolean {
        val candidates =
            buildList {
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

    /** [message] is plain text written by an admin, so it is escaped and its line breaks kept. */
    private fun buildPublicFormEmailBody(
        title: String,
        message: String?
    ): String =
        publicFormBodyTemplate
            .replace("{{TITLE}}", HtmlUtils.htmlEscape(title))
            .replace(
                "{{MESSAGE}}",
                message
                    ?.let { HtmlUtils.htmlEscape(it).replace("\n", "<br>") }
                    ?: HtmlUtils.htmlEscape(branding.submissionConfirmationMessage)
            )

    /**
     * Composes the final sender header from the deployment-specific address
     * ([NotificationEmailProperties.from]) and the [fromName] display text managed in the
     * branding file, e.g. `Aam Digital <notifications@example.org>`.
     *
     * Falls back to the bare configured address when no [fromName] is configured (or the address
     * is empty).
     */
    private fun resolveFrom(
        configuredFrom: String,
        fromName: String
    ): String {
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
            subjectPrefix = properties.getProperty("subject-prefix", DEFAULT_SUBJECT_PREFIX).trim(),
            submissionConfirmationLabel =
                properties
                    .getProperty("submission-confirmation-label", DEFAULT_SUBMISSION_CONFIRMATION_LABEL)
                    .trim(),
            submissionConfirmationMessage =
                properties
                    .getProperty("submission-confirmation-message", DEFAULT_SUBMISSION_CONFIRMATION_MESSAGE)
                    .trim()
        )
    }

    /**
     * Branding text for notification emails, managed in the `email-branding.properties` template
     * file rather than in deployment config.
     */
    private data class EmailBranding(
        val fromName: String,
        val subjectPrefix: String,
        val submissionConfirmationLabel: String,
        val submissionConfirmationMessage: String
    )

    companion object {
        private const val DEFAULT_SUBJECT_PREFIX = "Aam Digital"
        private const val DEFAULT_SUBMISSION_CONFIRMATION_LABEL = "Submission received"
        private const val DEFAULT_SUBMISSION_CONFIRMATION_MESSAGE =
            "This email confirms that your details were submitted successfully."
        private const val TEMPLATE_RELATIVE_PATH =
            "notification/create-notification-email-template.html"
        private const val PUBLIC_FORM_TEMPLATE_RELATIVE_PATH =
            "notification/public-form-confirmation-email-template.html"
        private const val BRANDING_RELATIVE_PATH =
            "notification/email-branding.properties"
        private val DEFAULT_TEMPLATES_BASE_DIR: Path =
            Paths.get("/opt/app/templates")
    }
}
