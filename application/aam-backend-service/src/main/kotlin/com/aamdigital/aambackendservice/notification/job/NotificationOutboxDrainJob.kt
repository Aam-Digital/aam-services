package com.aamdigital.aambackendservice.notification.job

import com.aamdigital.aambackendservice.common.outbox.Outbox
import com.aamdigital.aambackendservice.common.scheduling.ScheduledJobBackoff
import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationApiEnabled
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled

/**
 * Scheduled trigger for the notification [Outbox], delivering the push and email
 * notifications waiting in it (and in-app notifications whose immediate write failed).
 *
 * The interval is the delivery latency for those channels, so it is short; the query behind it is
 * cheap because delivered entries are removed and the outbox is empty in steady state.
 */
@Configuration
@ConditionalOnNotificationApiEnabled
class NotificationOutboxDrainJob(
    private val notificationOutbox: Outbox<CreateUserNotificationEvent>
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    internal val backoff = ScheduledJobBackoff(logger, "NotificationOutboxDrainJob")

    @Scheduled(fixedDelayString = "\${notification.outbox.fixed-delay:2000}")
    fun drainNotificationOutbox() {
        backoff.run {
            notificationOutbox.drain()
        }
    }
}
