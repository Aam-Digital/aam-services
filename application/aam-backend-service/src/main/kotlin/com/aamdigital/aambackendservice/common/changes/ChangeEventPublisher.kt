package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.queue.core.QueueMessage

interface ChangeEventPublisher {
    fun publish(
        exchange: String,
        event: DocumentChangeEvent
    ): QueueMessage
}
