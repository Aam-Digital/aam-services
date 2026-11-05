package com.aamdigital.aambackendservice.common.cache

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class LazySnapshotTest {
    /** Clock the test advances by hand, so no test depends on wall-clock timing. */
    private class MutableClock(
        var now: Instant = Instant.parse("2026-01-01T00:00:00Z")
    ) : Clock() {
        override fun instant(): Instant = now

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this
    }

    private var loads = 0

    private fun countingLoad(): Int = ++loads

    @Test
    fun `should load on first read and serve later reads from memory`() {
        val snapshot = LazySnapshot { countingLoad() }

        assertThat(loads).isZero()
        assertThat(snapshot.get()).isEqualTo(1)
        assertThat(snapshot.get()).isEqualTo(1)
        assertThat(loads).isEqualTo(1)
    }

    @Test
    fun `should load again after being invalidated`() {
        val snapshot = LazySnapshot { countingLoad() }
        snapshot.get()

        snapshot.invalidate()

        assertThat(snapshot.get()).isEqualTo(2)
    }

    @Test
    fun `should load again once the ttl has elapsed`() {
        val clock = MutableClock()
        val snapshot = LazySnapshot(ttl = Duration.ofSeconds(1), clock = clock) { countingLoad() }
        snapshot.get()

        clock.now = clock.now.plusMillis(999)
        assertThat(snapshot.get()).isEqualTo(1)

        clock.now = clock.now.plusMillis(1)
        assertThat(snapshot.get()).isEqualTo(2)
    }

    @Test
    fun `should load on every read when the ttl is zero`() {
        val snapshot = LazySnapshot(ttl = Duration.ZERO, clock = MutableClock()) { countingLoad() }

        snapshot.get()
        snapshot.get()

        assertThat(loads).isEqualTo(2)
    }

    @Test
    fun `should not cache a failed load, so the next read tries again`() {
        var fail = true
        val snapshot =
            LazySnapshot {
                if (fail) throw IllegalStateException("couchdb unreachable")
                countingLoad()
            }

        assertThatThrownBy { snapshot.get() }.hasMessage("couchdb unreachable")

        fail = false
        assertThat(snapshot.get()).isEqualTo(1)
    }

    @Test
    fun `should serve the previous copy when a reload fails, and try again after the retry interval`() {
        // given
        val clock = MutableClock()
        var fail = false
        val snapshot =
            LazySnapshot(
                ttl = Duration.ofHours(1),
                clock = clock,
                retryAfterFailedReload = Duration.ofMinutes(5)
            ) {
                if (fail) throw IllegalStateException("keycloak unreachable")
                countingLoad()
            }
        snapshot.get()

        // when the copy expires while loading fails
        fail = true
        clock.now = clock.now.plus(Duration.ofHours(1))

        // then
        assertThat(snapshot.get()).isEqualTo(1)

        fail = false
        clock.now = clock.now.plus(Duration.ofMinutes(4))
        assertThat(snapshot.get()).isEqualTo(1)

        clock.now = clock.now.plus(Duration.ofMinutes(1))
        assertThat(snapshot.get()).isEqualTo(2)
    }

    @Test
    fun `should still fail a first load that fails, as there is no previous copy to serve`() {
        val snapshot =
            LazySnapshot(retryAfterFailedReload = Duration.ofMinutes(5)) {
                throw IllegalStateException("couchdb unreachable")
            }

        assertThatThrownBy { snapshot.get() }.hasMessage("couchdb unreachable")
    }

    @Test
    fun `should apply an update to the loaded copy only`() {
        val snapshot = LazySnapshot { mapOf("a" to countingLoad()) }

        // nothing loaded yet: the change is left to the next load
        snapshot.update { it + ("b" to 0) }
        assertThat(snapshot.get()).isEqualTo(mapOf("a" to 1))

        snapshot.update { it + ("b" to 0) }
        assertThat(snapshot.get()).isEqualTo(mapOf("a" to 1, "b" to 0))
        assertThat(loads).isEqualTo(1)
    }
}
