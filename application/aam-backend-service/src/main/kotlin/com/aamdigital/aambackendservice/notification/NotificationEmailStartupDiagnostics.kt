package com.aamdigital.aambackendservice.notification

import com.aamdigital.aambackendservice.notification.NotificationEmailPrerequisites.REMOVED_FEATURE_FLAG
import com.aamdigital.aambackendservice.notification.NotificationEmailPrerequisites.SMTP_HOST
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.env.Environment

/**
 * Logs once at startup whether the email channel is on and, if not, which setting is missing.
 *
 * The channel is derived from configuration, so a missing setting is not an error in itself. It is
 * logged at ERROR only when an SMTP host is set, because then the operator clearly meant to send.
 * Only property names are logged, never their values.
 */
class NotificationEmailStartupDiagnostics(
    private val environment: Environment
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        logChannelState()
        warnIfRemovedFeatureFlagIsSet()
    }

    private fun warnIfRemovedFeatureFlagIsSet() {
        if (!environment.containsProperty(REMOVED_FEATURE_FLAG)) {
            return
        }

        logger.warn(
            "The property {} (FEATURES_NOTIFICATIONAPI_EMAIL_ENABLED) is no longer read and has no effect. " +
                "Notification email is on whenever the settings {} are all set. " +
                "Remove the property from the configuration.",
            REMOVED_FEATURE_FLAG,
            NotificationEmailPrerequisites.REQUIRED
        )
    }

    private fun logChannelState() {
        val missing = NotificationEmailPrerequisites.missing(environment)
        when {
            missing.isEmpty() -> {
                logger.info(
                    "Notification email is on: all of {} are configured.",
                    NotificationEmailPrerequisites.REQUIRED
                )
            }

            SMTP_HOST in missing -> {
                logger.info(
                    "Notification email is off: no SMTP host configured. To send notifications by email, set {}.",
                    NotificationEmailPrerequisites.REQUIRED
                )
            }

            else -> {
                logger.error(
                    "Notification email is off although an SMTP host is configured, " +
                        "because these required settings are missing: {}.",
                    missing
                )
            }
        }
    }
}
