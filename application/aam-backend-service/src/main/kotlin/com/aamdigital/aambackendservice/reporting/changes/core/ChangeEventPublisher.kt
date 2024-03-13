package com.aamdigital.aambackendservice.reporting.changes.core

import com.aamdigital.aambackendservice.domain.event.DocumentChangeEvent
import com.aamdigital.aambackendservice.queue.core.QueueMessage
import com.aamdigital.aambackendservice.reporting.changes.core.event.DatabaseChangeEvent

interface ChangeEventPublisher {
    fun publish(channel: String, event: DatabaseChangeEvent): QueueMessage
    fun publish(exchange: String, event: DocumentChangeEvent): QueueMessage
}
