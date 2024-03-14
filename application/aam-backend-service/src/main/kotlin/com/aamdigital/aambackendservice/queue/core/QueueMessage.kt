package com.aamdigital.aambackendservice.queue.core

import java.util.*

data class QueueMessage(
    val id: UUID,
    val type: String,
    val payload: Any,
    val createdAt: String,
    val spanId: String? = null,
    val traceId: String? = null,
)
