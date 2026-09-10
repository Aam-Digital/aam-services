package com.aamdigital.aambackendservice.notification.core.create

/**
 * Thrown when a notification fails due to a transient infrastructure error (e.g. SMTP connection timeout).
 *
 * Caught by [DefaultCreateNotificationUseCase.errorHandler] and re-thrown so it escapes
 * [com.aamdigital.aambackendservice.common.domain.DomainUseCase.run]'s blanket catch block.
 * This lets [com.aamdigital.aambackendservice.notification.core.outbox.NotificationOutboxDrainer]
 * retry it with backoff rather than parking it immediately as a permanent failure would.
 */
class TransientNotificationException(message: String, cause: Throwable) : RuntimeException(message, cause)
