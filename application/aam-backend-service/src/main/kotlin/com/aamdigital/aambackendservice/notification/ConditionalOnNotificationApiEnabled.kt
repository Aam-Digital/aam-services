package com.aamdigital.aambackendservice.notification

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/**
 * Activates the annotated bean only when the notification-api feature flag is enabled.
 * Single source of truth for the `features.notification-api.enabled` property.
 *
 * Stack with [ConditionalOnNotificationFirebaseMode] for beans that additionally require
 * the firebase notification mode.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(
    prefix = "features.notification-api",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
annotation class ConditionalOnNotificationApiEnabled
