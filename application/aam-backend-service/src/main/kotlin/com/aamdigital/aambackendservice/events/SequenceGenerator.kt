package com.aamdigital.aambackendservice.events

import java.util.concurrent.atomic.AtomicLong

// TODO -> needs database implementation or file persistence
// is needed to get system wide unique identifier for events
// easiest way is to use an Sequence from e.g. Postgres
object SequenceGenerator {
    private val currentId = AtomicLong(0)

    fun nextId(): Long {
        return currentId.incrementAndGet()
    }
}
