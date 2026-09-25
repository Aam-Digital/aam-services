package com.aamdigital.aambackendservice.notification.job

import com.aamdigital.aambackendservice.common.scheduling.ScheduledJobBackoff
import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationApiEnabled
import com.aamdigital.aambackendservice.notification.core.outbox.NotificationOutboxDrainer
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled

/**
 * Scheduled trigger for [NotificationOutboxDrainer], delivering the push and email notifications
 * waiting in the outbox.
 *
 * The interval is the delivery latency for those channels, so it is short; the query behind it is
 * cheap because delivered entries are removed and the outbox is empty in steady state.
 */
@Configuration
@ConditionalOnNotificationApiEnabled
class NotificationOutboxDrainJob(
    private val notificationOutboxDrainer: NotificationOutboxDrainer
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    internal val backoff = ScheduledJobBackoff(logger, "NotificationOutboxDrainJob")

    @Scheduled(fixedDelayString = "\${notification.outbox.fixed-delay:2000}")
    fun drainNotificationOutbox() {
        if (backoff.shouldSkip()) return

        backoff.execute {
            notificationOutboxDrainer.drain()
        }
    }
}
