package com.aamdigital.aambackendservice.notification

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/**
 * Activates the annotated bean only when the notification-api mode is set to `firebase`.
 * Single source of truth for `features.notification-api.mode=firebase`.
 *
 * Typically stacked with [ConditionalOnNotificationApiEnabled].
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(
    prefix = "features.notification-api",
    name = ["mode"],
    havingValue = "firebase",
    matchIfMissing = false,
)
annotation class ConditionalOnNotificationFirebaseMode
