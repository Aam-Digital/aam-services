package com.aamdigital.aambackendservice.queue.core

import com.aamdigital.aambackendservice.events.DomainEvent
import java.util.*

data class QueueMessage(
    val id: UUID,
    val eventType: String,
    val event: DomainEvent,
    val createdAt: String,
)
