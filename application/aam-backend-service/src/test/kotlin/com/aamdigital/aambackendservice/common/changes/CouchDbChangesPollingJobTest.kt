package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.scheduling.ScheduledJobBackoff
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CouchDbChangesPollingJobTest {
    private class NamedHandler(
        override val consumerName: String
    ) : DocumentChangeHandler {
        override fun handle(event: DocumentChangeEvent) = Unit
    }

    private val reporting = NamedHandler("reporting")
    private val notification = NamedHandler("notification")

    private fun job(
        processor: CouchDbChangesProcessor,
        handlers: List<DocumentChangeHandler> = listOf(reporting, notification),
        fixedDelay: Duration = Duration.ofSeconds(8)
    ) = CouchDbChangesPollingJob(
        changesProcessor = processor,
        documentChangeHandlers = handlers,
        fixedDelay = fixedDelay
    )

    @Test
    fun `should register one fixed-delay task per consumer`() {
        val registrar = ScheduledTaskRegistrar()

        job(mock(), fixedDelay = Duration.ofMillis(8000)).configureTasks(registrar)

        assertThat(registrar.fixedDelayTaskList).hasSize(2)
        assertThat(registrar.fixedDelayTaskList.map { it.intervalDuration }).containsOnly(Duration.ofMillis(8000))
    }

    @Test
    fun `should poll each consumer for itself`() {
        val processor = mock<CouchDbChangesProcessor>()
        val job = job(processor)

        job.pollers.single { it.consumerName == "notification" }.run()

        verify(processor).checkForChanges(notification)
        verify(processor, never()).checkForChanges(reporting)
    }

    @Test
    fun `should reject two handlers with the same consumer name, since they would share one cursor`() {
        assertThatThrownBy { job(mock(), handlers = listOf(reporting, NamedHandler("reporting"))) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("reporting")
    }

    @Test
    fun `should keep polling for one consumer while another one is stuck`() {
        // Given
        val notificationStuck = CountDownLatch(1)
        val releaseNotification = CountDownLatch(1)
        val reportingPolls = AtomicInteger()
        val processor =
            mock<CouchDbChangesProcessor> {
                on { checkForChanges(notification) } doAnswer {
                    notificationStuck.countDown()
                    releaseNotification.await(10, TimeUnit.SECONDS)
                    Unit
                }
                on { checkForChanges(reporting) } doAnswer {
                    reportingPolls.incrementAndGet()
                    Unit
                }
            }
        val scheduler =
            ThreadPoolTaskScheduler().apply {
                poolSize = 2
                initialize()
            }
        val registrar = ScheduledTaskRegistrar().apply { setTaskScheduler(scheduler) }
        job(processor, fixedDelay = Duration.ofMillis(10)).configureTasks(registrar)

        try {
            // When
            registrar.afterPropertiesSet()

            // Then
            assertThat(notificationStuck.await(5, TimeUnit.SECONDS)).isTrue()
            val pollsWhileStuck = reportingPolls.get()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (reportingPolls.get() < pollsWhileStuck + 3 && System.nanoTime() < deadline) {
                Thread.sleep(10)
            }
            assertThat(reportingPolls.get()).isGreaterThanOrEqualTo(pollsWhileStuck + 3)
            assertThat(releaseNotification.count).isEqualTo(1)
        } finally {
            releaseNotification.countDown()
            registrar.destroy()
            scheduler.shutdown()
        }
    }

    /** Each consumer backs off on its own, so one failing consumer does not slow the others down. */
    @Nested
    inner class ChangeConsumerPollerBackoff {
        private var currentTime: Long = 0L
        private var polls = 0
        private val outcomes = ArrayDeque<Boolean>()

        /** fails while [outcomes] says so, succeeds once it runs out */
        private val poller =
            ChangeConsumerPoller("test") {
                polls++
                if (outcomes.removeFirstOrNull() == false) throw RuntimeException("error")
            }.also { it.backoff.clock = { currentTime } }

        private fun failNext(times: Int) = repeat(times) { outcomes.addLast(false) }

        @Test
        fun `should poll on each invocation`() {
            poller.run()

            assertThat(polls).isEqualTo(1)
        }

        @Test
        fun `should skip execution during backoff period and resume after`() {
            failNext(3)

            // First failure sets nextRetryAt = currentTime + 5000
            poller.run()
            assertThat(polls).isEqualTo(1)

            // Still within backoff — should be skipped
            currentTime = 4999L
            poller.run()
            assertThat(polls).isEqualTo(1)

            // Backoff elapsed — should retry
            currentTime = 5000L
            poller.run()
            assertThat(polls).isEqualTo(2)
        }

        @Test
        fun `should increase backoff delay on consecutive failures`() {
            failNext(4)

            poller.run() // 1st failure: backoff = 5000ms
            currentTime = 5000L
            poller.run() // 2nd failure: backoff = 10000ms
            currentTime = 15000L
            poller.run() // 3rd failure: backoff = 20000ms

            // At 34999ms — still within third backoff (15000 + 20000 = 35000)
            currentTime = 34999L
            poller.run()
            assertThat(polls).isEqualTo(3)

            // At 35000ms — backoff elapsed, should retry
            currentTime = 35000L
            poller.run()
            assertThat(polls).isEqualTo(4)
        }

        @Test
        fun `should cap backoff delay at 24 hours`() {
            failNext(25)

            repeat(20) {
                poller.run()
                currentTime += ScheduledJobBackoff.MAX_BACKOFF_MS
            }
            val pollsBefore = polls

            poller.run()
            currentTime += ScheduledJobBackoff.MAX_BACKOFF_MS - 1
            poller.run()
            assertThat(polls).isEqualTo(pollsBefore + 1)

            currentTime += 1
            poller.run()
            assertThat(polls).isEqualTo(pollsBefore + 2)
        }

        @Test
        fun `should reset backoff on success after failures`() {
            failNext(3)
            outcomes.addLast(true)
            failNext(1)

            poller.run() // fail 1: backoff 5s
            currentTime = 5000L
            poller.run() // fail 2: backoff 10s
            currentTime = 15000L
            poller.run() // fail 3: backoff 20s
            currentTime = 35000L

            // Success — resets counter
            poller.run()

            // Next failure should start backoff from scratch (5000ms, not 40000ms)
            poller.run()

            currentTime = 35000L + 4999L
            poller.run()
            assertThat(polls).isEqualTo(5)

            currentTime = 35000L + 5000L
            poller.run()
            assertThat(polls).isEqualTo(6)
        }
    }
}
