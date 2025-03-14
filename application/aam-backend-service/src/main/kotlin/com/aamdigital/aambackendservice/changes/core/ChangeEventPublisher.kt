package com.aamdigital.aambackendservice.changes.core

import com.aamdigital.aambackendservice.changes.domain.DatabaseChangeEvent
import com.aamdigital.aambackendservice.changes.domain.DocumentChangeEvent
import com.aamdigital.aambackendservice.queue.core.QueueMessage

interface ChangeEventPublisher {
    fun publish(channel: String, event: DatabaseChangeEvent): QueueMessage
    fun publish(exchange: String, event: DocumentChangeEvent): QueueMessage
}
