package com.aamdigital.aambackendservice.common.execution

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

class BoundedTaskRunnerTest {
    @Test
    fun `should count work as in flight from submission until it finishes`() {
        // Given a sweeper must never mistake accepted work for work nothing is running
        val submitted = mutableListOf<Runnable>()
        val runner = BoundedTaskRunner("test", Executor { submitted.add(it) })
        var ran = false

        // When
        val accepted = runner.submit("task-1") { ran = true }

        // Then
        assertThat(accepted).isTrue()
        assertThat(runner.inFlight()).containsExactly("task-1")

        // When the queued work runs
        submitted.single().run()

        // Then
        assertThat(ran).isTrue()
        assertThat(runner.inFlight()).isEmpty()
    }

    @Test
    fun `should contain a failing task and forget it`() {
        // Given nothing above the task is on a caller's stack
        val runner = BoundedTaskRunner("test", Executor { it.run() })

        // When
        val accepted = runner.submit("task-1") { throw IllegalStateException("boom") }

        // Then
        assertThat(accepted).isTrue()
        assertThat(runner.inFlight()).isEmpty()
    }

    @Test
    fun `should report a rejected task instead of throwing, and not count it as in flight`() {
        // Given
        val runner = BoundedTaskRunner("test", Executor { throw RejectedExecutionException("saturated") })
        var ran = false

        // When
        val accepted = runner.submit("task-1") { ran = true }

        // Then
        assertThat(accepted).isFalse()
        assertThat(ran).isFalse()
        assertThat(runner.inFlight()).isEmpty()
    }

    @Test
    fun `should build a pool that runs at most the given number of tasks at once`() {
        // When
        val pool =
            BoundedTaskRunner.threadPool(
                name = "test",
                concurrency = 2,
                backlog = 10,
                shutdownTimeout = Duration.ofSeconds(5)
            )

        // Then the pool never grows past its concurrency, whatever the backlog
        assertThat(pool.corePoolSize).isEqualTo(2)
        assertThat(pool.maxPoolSize).isEqualTo(2)
        assertThat(pool.queueCapacity).isEqualTo(10)
    }
}
