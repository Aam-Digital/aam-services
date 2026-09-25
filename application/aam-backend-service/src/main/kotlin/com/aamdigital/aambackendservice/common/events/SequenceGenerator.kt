package com.aamdigital.aambackendservice.common.events

import java.util.concurrent.atomic.AtomicLong

// TODO -> needs database implementation or file persistence
// is needed to get system wide unique identifier for events
object SequenceGenerator {
    private val currentId = AtomicLong(0)

    fun nextId(): Long = currentId.incrementAndGet()
}
