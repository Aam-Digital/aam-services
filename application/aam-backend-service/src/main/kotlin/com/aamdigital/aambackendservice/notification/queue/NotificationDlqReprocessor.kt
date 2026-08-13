package com.aamdigital.aambackendservice.notification.queue

import com.aamdigital.aambackendservice.notification.di.NotificationQueueConfiguration.Companion.USER_NOTIFICATION_DLQ
import com.aamdigital.aambackendservice.notification.di.NotificationQueueConfiguration.Companion.USER_NOTIFICATION_QUEUE
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpConnectException
import org.springframework.amqp.AmqpException
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Drains [USER_NOTIFICATION_DLQ] back into [USER_NOTIFICATION_QUEUE] once per service lifetime, so
 * that notifications which previously failed — whether due to exhausted transient retries or a
 * permanent failure such as bad SMTP credentials — are retried once after the service restarts.
 *
 * This gives operators a simple recovery path: fix the root cause (e.g. update credentials,
 * open the firewall port) and restart the service.
 *
 * Runs on a schedule and retries until the broker answers, rather than draining once from
 * `ApplicationReadyEvent`. An exception thrown from an `ApplicationReadyEvent` listener aborts the
 * whole run — Spring Boot closes the context and the process exits — so a broker that was briefly
 * unreachable at boot used to crash-loop the service instead of merely postponing this best-effort
 * recovery step.
 *
 * The drain still happens at most once per process, so a message that always fails is re-queued
 * once per restart rather than once per retry.
 */
class NotificationDlqReprocessor(
    private val amqpAdmin: AmqpAdmin,
    private val dlq: Queue,
    private val rabbitTemplate: RabbitTemplate
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val drained = AtomicBoolean(false)
    private val permanentFailureLogged = AtomicBoolean(false)

    @Scheduled(fixedDelayString = "\${notification.dlq-reprocessing.fixed-delay:30000}")
    fun reprocessDeadLetteredNotifications() {
        if (drained.get()) return

        try {
            val count = drainDeadLetterQueue()
            drained.set(true)

            if (count > 0) {
                logger.info(
                    "Re-queued {} message(s) from {} back to {}",
                    count,
                    USER_NOTIFICATION_DLQ,
                    USER_NOTIFICATION_QUEUE
                )
            }
        } catch (ex: AmqpConnectException) {
            // Expected while the broker is starting or being restarted. WARN sits below the Sentry
            // minimum event level, so routine maintenance stays out of Sentry; the next tick retries.
            logger.warn("Broker unreachable, postponing dead letter reprocessing: {}", ex.message)
        } catch (ex: AmqpException) {
            // Anything else will not resolve itself - a 406 from a queue previously declared with
            // different arguments, or a permissions problem. Report it once so it is visible, then
            // keep retrying quietly instead of repeating the same error on every tick.
            if (permanentFailureLogged.compareAndSet(false, true)) {
                logger.error("Could not reprocess dead lettered notifications: {}", ex.message, ex)
            }
        }
    }

    private fun drainDeadLetterQueue(): Int {
        amqpAdmin.declareQueue(dlq)

        var count = 0
        while (true) {
            val message = rabbitTemplate.receive(USER_NOTIFICATION_DLQ) ?: break
            rabbitTemplate.send("", USER_NOTIFICATION_QUEUE, message)
            count++
        }
        return count
    }
}
