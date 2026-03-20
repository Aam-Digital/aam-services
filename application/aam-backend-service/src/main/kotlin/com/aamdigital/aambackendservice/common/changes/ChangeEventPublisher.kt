package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.queue.core.QueueMessage

/**
 * Publishes [DocumentChangeEvent]s to a message broker exchange.
 * See [DefaultChangeEventPublisher] for the RabbitMQ implementation.
 */
interface ChangeEventPublisher {
    fun publish(
        exchange: String,
        event: DocumentChangeEvent
    ): QueueMessage
}
