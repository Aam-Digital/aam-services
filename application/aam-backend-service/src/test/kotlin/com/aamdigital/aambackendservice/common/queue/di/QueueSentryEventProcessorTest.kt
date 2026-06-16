package com.aamdigital.aambackendservice.common.queue.di

import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Date

class QueueSentryEventProcessorTest {
    private val processor = QueueSentryEventProcessor(throttleWindow = Duration.ofMinutes(5))

    private fun redeclareEvent(atEpochMillis: Long): SentryEvent =
        SentryEvent(Date(atEpochMillis)).apply {
            message = Message().apply { formatted = REDECLARE_MESSAGE }
        }

    @Test
    fun `forwards unrelated events unchanged`() {
        val event =
            SentryEvent(Date(0)).apply {
                message = Message().apply { formatted = "some unrelated error" }
            }

        assertThat(processor.process(event, Hint())).isSameAs(event)
    }

    @Test
    fun `forwards the first redeclare event but drops repeats within the throttle window`() {
        val first = redeclareEvent(0)
        val withinWindow = redeclareEvent(Duration.ofMinutes(1).toMillis())

        assertThat(processor.process(first, Hint())).isSameAs(first)
        assertThat(processor.process(withinWindow, Hint())).isNull()
    }

    @Test
    fun `forwards a redeclare event again once the throttle window has elapsed`() {
        val first = redeclareEvent(0)
        val afterWindow = redeclareEvent(Duration.ofMinutes(5).toMillis() + 1)

        assertThat(processor.process(first, Hint())).isSameAs(first)
        assertThat(processor.process(afterWindow, Hint())).isSameAs(afterWindow)
    }

    companion object {
        private const val REDECLARE_MESSAGE = "Failed to check/redeclare auto-delete queue(s)."
    }
}
