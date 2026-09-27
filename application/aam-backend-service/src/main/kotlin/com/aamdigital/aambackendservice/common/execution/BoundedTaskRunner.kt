package com.aamdigital.aambackendservice.common.execution

import org.slf4j.LoggerFactory
import org.springframework.core.NestedExceptionUtils
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * Runs work off the caller's thread, on an [executor] whose concurrency and backlog are bounded
 * (see [threadPool]), so slow work cannot hold up the caller and a flood of work is rejected rather
 * than queued without limit.
 *
 * It owns what every such handoff needs, so the module only passes the work itself:
 *
 * - [submit] reports a rejection (executor saturated or shutting down) as `false` instead of
 *   throwing; whether that loses the work, and how loudly to log it, is the caller's call;
 * - an exception escaping the work is logged at ERROR with its root cause, which is how it reaches
 *   Sentry: nothing above it is on a caller's stack;
 * - [inFlight] tells which keys were accepted and have not finished, for a sweeper that recovers
 *   work whose durable record says it is owed but nothing is running it.
 *
 * @param name used in log messages
 */
class BoundedTaskRunner(
    private val name: String,
    private val executor: Executor
) {
    companion object {
        /**
         * A thread pool that runs at most [concurrency] tasks at once and holds at most [backlog]
         * waiting ones. Return it from a `@Bean` method, so Spring initializes it and, on shutdown,
         * gives running and queued tasks [shutdownTimeout] to finish.
         */
        fun threadPool(
            name: String,
            concurrency: Int,
            backlog: Int,
            shutdownTimeout: Duration
        ): ThreadPoolTaskExecutor =
            ThreadPoolTaskExecutor().apply {
                // core == max: the pool only grows past its core size once the backlog is full,
                // so a larger max would never be reached in practice
                corePoolSize = concurrency
                maxPoolSize = concurrency
                setQueueCapacity(backlog)
                setThreadNamePrefix("$name-")
                setWaitForTasksToCompleteOnShutdown(true)
                setAwaitTerminationSeconds(shutdownTimeout.toSeconds().toInt())
            }
    }

    private val logger = LoggerFactory.getLogger(javaClass)
    private val inFlightKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Keys that were accepted and have not finished yet. */
    fun inFlight(): Set<String> = inFlightKeys.toSet()

    /**
     * Hands [task] to the executor.
     *
     * @param key identifies the task in [inFlight] and in log messages
     * @return false when the executor rejected the task, which then never runs
     */
    fun submit(
        key: String,
        task: () -> Unit
    ): Boolean {
        // marked before submitting, so a concurrent reader of inFlight() never misses accepted work
        inFlightKeys.add(key)

        return try {
            executor.execute { run(key, task) }
            true
        } catch (ex: RejectedExecutionException) {
            inFlightKeys.remove(key)
            logger.debug("{} rejected {}", name, key, ex)
            false
        }
    }

    private fun run(
        key: String,
        task: () -> Unit
    ) {
        try {
            task()
        } catch (ex: Exception) {
            val rootCause = NestedExceptionUtils.getMostSpecificCause(ex)
            logger.error("{} failed for {}: {}", name, key, rootCause.message, rootCause)
        } finally {
            inFlightKeys.remove(key)
        }
    }
}
