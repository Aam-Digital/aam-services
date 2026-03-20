package com.aamdigital.aambackendservice.common.changes.core

import com.aamdigital.aambackendservice.common.changes.domain.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.queue.core.QueueMessage

interface ChangeEventPublisher {
    fun publish(
        exchange: String,
        event: DocumentChangeEvent
    ): QueueMessage
}
