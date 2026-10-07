package com.aamdigital.aambackendservice.notification

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.env.PropertyResolver
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * What the notification module needs to deliver by email. There is no feature flag for the channel:
 * it is active exactly when all of these properties have a non-blank value, so the SMTP settings are
 * the only place where an operator states the intent.
 *
 * A blank value counts as not set. The deployment env template ships these keys with an empty value
 * (e.g. `SPRING_MAIL_HOST=`), which the plain `@ConditionalOnProperty` would treat as set.
 */
object NotificationEmailPrerequisites {
    /** SMTP server to send through. Its presence states the intent to send notifications by email. */
    const val SMTP_HOST = "spring.mail.host"

    /** Sender address; without it every email would be rejected by the SMTP server and retried. */
    const val SENDER_ADDRESS = "notification.email.from"

    /** Keycloak access, needed to look up the recipients' email addresses. */
    const val KEYCLOAK_SERVER_URL = "keycloak.server-url"

    /** The former on/off switch of the channel, which is no longer read. */
    const val REMOVED_FEATURE_FLAG = "features.notification-api.email.enabled"

    /** All properties that have to be set for the channel to be on. */
    val REQUIRED = listOf(SMTP_HOST, SENDER_ADDRESS, KEYCLOAK_SERVER_URL)

    /** The required properties that are not set or blank, in the order of [REQUIRED]. */
    fun missing(properties: PropertyResolver): List<String> =
        REQUIRED.filter { properties.getProperty(it).isNullOrBlank() }
}

/** Matches when [NotificationEmailPrerequisites.missing] has nothing to report. */
class NotificationEmailConfiguredCondition : Condition {
    override fun matches(
        context: ConditionContext,
        metadata: AnnotatedTypeMetadata
    ): Boolean = NotificationEmailPrerequisites.missing(context.environment).isEmpty()
}
