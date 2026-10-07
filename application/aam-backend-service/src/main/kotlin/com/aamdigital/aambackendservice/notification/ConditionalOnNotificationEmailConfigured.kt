package com.aamdigital.aambackendservice.notification

import org.springframework.context.annotation.Conditional

/**
 * Activates the annotated bean only when notification emails can be sent: the notification API is
 * enabled and the SMTP host, the sender address and the Keycloak access are configured
 * (see [NotificationEmailPrerequisites]).
 *
 * Single source of truth for whether the email channel exists. It implies
 * [ConditionalOnNotificationApiEnabled], so it does not have to be stacked with it.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnNotificationApiEnabled
@Conditional(NotificationEmailConfiguredCondition::class)
annotation class ConditionalOnNotificationEmailConfigured
