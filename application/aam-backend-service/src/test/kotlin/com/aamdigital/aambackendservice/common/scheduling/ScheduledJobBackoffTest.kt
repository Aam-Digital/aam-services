package com.aamdigital.aambackendservice.common.scheduling

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class ScheduledJobBackoffTest {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Test
    fun doublesBackoffUpToDefaultCapOf24Hours() {
        assertThat(ScheduledJobBackoff.calculateBackoffMs(1)).isEqualTo(5_000L)
        assertThat(ScheduledJobBackoff.calculateBackoffMs(2)).isEqualTo(10_000L)
        assertThat(ScheduledJobBackoff.calculateBackoffMs(3)).isEqualTo(20_000L)
        assertThat(ScheduledJobBackoff.calculateBackoffMs(100)).isEqualTo(ScheduledJobBackoff.DEFAULT_MAX_BACKOFF_MS)
    }

    @Test
    fun capsBackoffAtGivenMaximum() {
        assertThat(ScheduledJobBackoff.calculateBackoffMs(6, maxBackoffMs = 60_000L)).isEqualTo(60_000L)
        assertThat(ScheduledJobBackoff.calculateBackoffMs(100, maxBackoffMs = 60_000L)).isEqualTo(60_000L)
    }

    @Test
    fun retriesAtCustomMaximumOnceReached() {
        // given
        var currentTime = 0L
        var runs = 0
        val backoff = ScheduledJobBackoff(logger, "test", maxBackoffMs = 60_000L, clock = { currentTime })
        val failingAction = {
            runs++
            throw RuntimeException("error")
        }

        // when: failing for long enough to reach the cap (5s, 10s, 20s, 40s, then 60s)
        repeat(10) {
            backoff.run(failingAction)
            currentTime += 60_000L
        }
        val runsBefore = runs
        backoff.run(failingAction)

        // then: skipped until exactly the cap has elapsed
        currentTime += 59_999L
        backoff.run(failingAction)
        assertThat(runs).isEqualTo(runsBefore + 1)

        currentTime += 1L
        backoff.run(failingAction)
        assertThat(runs).isEqualTo(runsBefore + 2)
    }

    @Test
    fun rejectsMaximumBelowInitialBackoff() {
        assertThatThrownBy { ScheduledJobBackoff(logger, "test", maxBackoffMs = 1_000L) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
