package com.aamdigital.aambackendservice.common.execution

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration

class InlineRetryTest {
    @AfterEach
    fun clearInterrupt() {
        Thread.interrupted()
    }

    @Test
    fun `should stop retrying once an attempt succeeds`() {
        // Given
        var calls = 0

        // When
        val succeeded =
            InlineRetry(attempts = 3, initialInterval = Duration.ZERO).run("doing the thing") {
                calls++
                if (calls < 2) throw IllegalStateException("not yet")
            }

        // Then
        assertThat(succeeded).isTrue()
        assertThat(calls).isEqualTo(2)
    }

    @Test
    fun `should give up after the configured attempts without throwing`() {
        // Given
        var calls = 0

        // When
        val succeeded =
            InlineRetry(attempts = 3, initialInterval = Duration.ZERO).run("doing the thing") {
                calls++
                throw IllegalStateException("never")
            }

        // Then
        assertThat(succeeded).isFalse()
        assertThat(calls).isEqualTo(3)
    }

    @Test
    fun `should keep the thread's interrupt flag when interrupted while waiting`() {
        // Given
        Thread.currentThread().interrupt()
        var calls = 0

        // When
        InlineRetry(attempts = 2, initialInterval = Duration.ofSeconds(10)).run("doing the thing") {
            calls++
            throw IllegalStateException("never")
        }

        // Then the wait was cut short, and the interrupt is not swallowed
        assertThat(calls).isEqualTo(2)
        assertThat(Thread.currentThread().isInterrupted).isTrue()
    }
}
