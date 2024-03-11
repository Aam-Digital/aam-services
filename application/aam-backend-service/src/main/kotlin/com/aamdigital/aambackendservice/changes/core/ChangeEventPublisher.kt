package com.aamdigital.aambackendservice.changes.core

import com.aamdigital.aambackendservice.changes.core.event.DatabaseChangeEvent
import com.aamdigital.aambackendservice.domain.event.DocumentChangeEvent
import com.aamdigital.aambackendservice.queue.core.QueueMessage

interface ChangeEventPublisher {
    fun publish(channel: String, event: DatabaseChangeEvent): QueueMessage
    fun publish(exchange: String, event: DocumentChangeEvent): QueueMessage
}
