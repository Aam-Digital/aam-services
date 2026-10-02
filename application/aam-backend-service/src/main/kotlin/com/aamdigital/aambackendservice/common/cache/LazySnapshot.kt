package com.aamdigital.aambackendservice.common.cache

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * An in-memory copy of something expensive to read, loaded on first use and kept until
 * [invalidate] is called or, with a [ttl], until it is older than that.
 *
 * Loading lazily rather than at startup means a read is never answered from a copy that has not
 * finished loading. A failed load throws to the reader and caches nothing, so the next read tries
 * again instead of serving an empty result.
 *
 * The load runs while holding the lock, by design: the readers are single-threaded change-detection
 * paths, so there is no concurrency to trade away, and the lock rules out two loads for one miss.
 *
 * @param ttl how long a loaded copy may be served; `null` keeps it until [invalidate], and zero
 *     reloads on every read
 * @param load reads the current value
 */
class LazySnapshot<T : Any>(
    private val ttl: Duration? = null,
    private val clock: Clock = Clock.systemUTC(),
    private val load: () -> T
) {
    private val lock = Any()
    private var value: T? = null
    private var loadedAt: Instant = Instant.EPOCH

    /** The loaded copy, loading it first if there is none or it has expired. */
    fun get(): T =
        synchronized(lock) {
            val now = clock.instant()
            value?.takeUnless { ttl != null && Duration.between(loadedAt, now) >= ttl }
                ?: load().also { loaded ->
                    value = loaded
                    loadedAt = now
                }
        }

    /** Drops the loaded copy without any I/O, so the next [get] loads again. */
    fun invalidate() {
        synchronized(lock) {
            value = null
        }
    }

    /**
     * Applies an incremental change to the loaded copy. Does nothing while nothing is loaded: the
     * next load reads the current state, which already includes the change.
     */
    fun update(transform: (T) -> T) {
        synchronized(lock) {
            value = value?.let(transform)
        }
    }
}
