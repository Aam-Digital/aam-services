package com.aamdigital.aambackendservice.events

import java.util.concurrent.atomic.AtomicLong

// TODO -> needs database implementation or file persistence
object SequenceGenerator {
    private val currentId = AtomicLong(0)

    fun nextId(): Long {
        return currentId.incrementAndGet()
    }
}

