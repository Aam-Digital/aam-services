package com.aamdigital.aambackendservice.notification.queue

import com.aamdigital.aambackendservice.notification.di.NotificationQueueConfiguration.Companion.USER_NOTIFICATION_DLQ
import com.aamdigital.aambackendservice.notification.di.NotificationQueueConfiguration.Companion.USER_NOTIFICATION_QUEUE
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener

/**
 * On startup, drains all messages from [USER_NOTIFICATION_DLQ] back into [USER_NOTIFICATION_QUEUE]
 * so that notifications which previously failed — whether due to exhausted transient retries or a
 * permanent failure such as bad SMTP credentials — are retried once after the service restarts.
 *
 * This gives operators a simple recovery path: fix the root cause (e.g. update credentials,
 * open the firewall port) and restart the service.
 */
class StartupNotificationDlqReprocessor(
    private val rabbitAdmin: RabbitAdmin,
    private val dlq: Queue,
    private val rabbitTemplate: RabbitTemplate,
) : ApplicationListener<ApplicationReadyEvent> {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        rabbitAdmin.declareQueue(dlq)

        var count = 0
        while (true) {
            val message = rabbitTemplate.receive(USER_NOTIFICATION_DLQ) ?: break
            rabbitTemplate.send("", USER_NOTIFICATION_QUEUE, message)
            count++
        }
        if (count > 0) {
            logger.info("Re-queued {} message(s) from {} back to {}", count, USER_NOTIFICATION_DLQ, USER_NOTIFICATION_QUEUE)
        }
    }
}
