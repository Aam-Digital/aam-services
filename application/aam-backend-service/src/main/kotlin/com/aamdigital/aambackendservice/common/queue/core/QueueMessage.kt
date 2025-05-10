package com.aamdigital.aambackendservice.common.queue.core

import com.aamdigital.aambackendservice.common.events.DomainEvent
import java.util.*

data class QueueMessage(
    val id: UUID,
    val eventType: String,
    val event: DomainEvent,
    val createdAt: String,
)
