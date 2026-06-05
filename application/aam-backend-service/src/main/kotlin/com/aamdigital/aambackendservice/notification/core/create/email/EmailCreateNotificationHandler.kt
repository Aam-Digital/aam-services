package com.aamdigital.aambackendservice.notification.core.create.email

import com.aamdigital.aambackendservice.common.mail.MailSenderRequest
import com.aamdigital.aambackendservice.common.mail.MailSenderService
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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.charset.StandardCharsets

class EmailCreateNotificationHandler(
    private val mailSenderService: MailSenderService,
    private val userEmailProvider: UserEmailProvider,
    private val notificationEmailProperties: NotificationEmailProperties,
    private val templatesBaseDir: Path = DEFAULT_TEMPLATES_BASE_DIR
) : CreateNotificationHandler {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val emailBodyTemplate: String = loadEmailBodyTemplate(normalizeLocale(notificationEmailProperties.locale))

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
            try {
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
     * Reduces a (possibly region-qualified) locale to the base language code used
     * for the template folder name, e.g. `en-US` -> `en`, `de_DE` -> `de`.
     * Falls back to [DEFAULT_LOCALE] for blank input.
     */
    private fun normalizeLocale(locale: String): String =
        locale.substringBefore('-').substringBefore('_').trim().lowercase().ifBlank { DEFAULT_LOCALE }

    /**
     * Resolves the email template for the given (normalized) locale, trying, in order:
     *  1. mounted override, localized: `{baseDir}/{locale}/notification/...`
     *  2. mounted override, legacy unsuffixed (back-compat): `{baseDir}/notification/...`
     *  3. bundled classpath, localized: `/templates/{locale}/notification/...`
     *  4. bundled classpath, default English: `/templates/en/notification/...`
     */
    private fun loadEmailBodyTemplate(locale: String): String {
        val localizedOverride = templatesBaseDir.resolve(locale).resolve(TEMPLATE_RELATIVE_PATH)
        if (Files.isRegularFile(localizedOverride)) {
            logger.info("Loading notification email template from {}", localizedOverride)
            return Files.readString(localizedOverride, StandardCharsets.UTF_8)
        }

        val legacyOverride = templatesBaseDir.resolve(TEMPLATE_RELATIVE_PATH)
        if (Files.isRegularFile(legacyOverride)) {
            logger.info("Loading notification email template from legacy override {}", legacyOverride)
            return Files.readString(legacyOverride, StandardCharsets.UTF_8)
        }

        val localizedClasspath = "/$TEMPLATES_CLASSPATH_ROOT/$locale/$TEMPLATE_RELATIVE_PATH"
        javaClass.getResourceAsStream(localizedClasspath)?.use { resource ->
            logger.info("Loading notification email template from classpath {}", localizedClasspath)
            return resource.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        }

        val defaultClasspath = "/$TEMPLATES_CLASSPATH_ROOT/$DEFAULT_LOCALE/$TEMPLATE_RELATIVE_PATH"
        val resource = javaClass.getResourceAsStream(defaultClasspath)
            ?: throw IllegalStateException("Missing email template resource: $defaultClasspath")

        logger.info("Loading notification email template from classpath fallback {}", defaultClasspath)
        return resource
            .bufferedReader(StandardCharsets.UTF_8)
            .use { it.readText() }
    }

    companion object {
        private const val DEFAULT_LOCALE = "en"
        private const val TEMPLATES_CLASSPATH_ROOT = "templates"
        private const val TEMPLATE_RELATIVE_PATH =
            "notification/create-notification-email-template.html"
        private val DEFAULT_TEMPLATES_BASE_DIR: Path =
            Paths.get("/opt/app/templates")
    }
}
