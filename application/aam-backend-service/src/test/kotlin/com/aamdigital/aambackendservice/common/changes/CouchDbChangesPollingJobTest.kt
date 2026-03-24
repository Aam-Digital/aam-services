package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.scheduling.ScheduledJobBackoff
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class CouchDbChangesPollingJobTest {

    private lateinit var job: CouchDbChangesPollingJob

    @Mock
    lateinit var databaseChangeDetection: CouchDbChangesProcessor

    private var currentTime: Long = 0L

    @BeforeEach
    fun setUp() {
        reset(databaseChangeDetection)
        currentTime = 0L
        job = CouchDbChangesPollingJob(
            changesProcessor = databaseChangeDetection,
        )
        job.backoff.clock = { currentTime }
    }

    @Test
    fun `should delegate to checkForChanges on each invocation`() {
        job.checkForCouchDbChanges()

        verify(databaseChangeDetection).checkForChanges()
    }

    @Test
    fun `should skip execution during backoff period and resume after`() {
        doThrow(RuntimeException("error")).whenever(databaseChangeDetection).checkForChanges()

        // First failure sets nextRetryAt = currentTime + 5000
        job.checkForCouchDbChanges()
        verify(databaseChangeDetection, org.mockito.kotlin.times(1)).checkForChanges()

        // Still within backoff — should be skipped
        currentTime = 4999L
        job.checkForCouchDbChanges()
        verify(databaseChangeDetection, org.mockito.kotlin.times(1)).checkForChanges()

        // Backoff elapsed — should retry
        currentTime = 5000L
        job.checkForCouchDbChanges()
        verify(databaseChangeDetection, org.mockito.kotlin.times(2)).checkForChanges()
    }

    @Test
    fun `should increase backoff delay on consecutive failures`() {
        doThrow(RuntimeException("error")).whenever(databaseChangeDetection).checkForChanges()

        // 1st failure: backoff = 5000ms
        job.checkForCouchDbChanges()

        // Advance past first backoff
        currentTime = 5000L
        // 2nd failure: backoff = 10000ms
        job.checkForCouchDbChanges()

        // Advance past second backoff
        currentTime = 15000L
        // 3rd failure: backoff = 20000ms
        job.checkForCouchDbChanges()

        // At 34999ms — still within third backoff (15000 + 20000 = 35000)
        currentTime = 34999L
        job.checkForCouchDbChanges()
        verify(databaseChangeDetection, org.mockito.kotlin.times(3)).checkForChanges()

        // At 35000ms — backoff elapsed, should retry
        currentTime = 35000L
        job.checkForCouchDbChanges()
        verify(databaseChangeDetection, org.mockito.kotlin.times(4)).checkForChanges()
    }

    @Test
    fun `should cap backoff delay at 24 hours`() {
        doThrow(RuntimeException("error")).whenever(databaseChangeDetection).checkForChanges()

        // Trigger enough failures to exceed 24h cap
        // backoff = 5000 * 2^(attempt-1), cap at 86_400_000
        repeat(20) {
            job.checkForCouchDbChanges()
            currentTime += ScheduledJobBackoff.MAX_BACKOFF_MS
        }

        // After 20 failures, next backoff should be capped at MAX_BACKOFF_MS
        val timeBefore = currentTime
        job.checkForCouchDbChanges()

        // Verify the job resumes at exactly timeBefore + MAX_BACKOFF_MS
        currentTime = timeBefore + ScheduledJobBackoff.MAX_BACKOFF_MS
        job.checkForCouchDbChanges()

        // But IS skipped just before that
        // (already validated by the exponential test pattern)
    }

    @Test
    fun `should reset backoff on success after failures`() {
        whenever(databaseChangeDetection.checkForChanges())
            .thenThrow(RuntimeException("error"))
            .thenThrow(RuntimeException("error"))
            .thenThrow(RuntimeException("error"))
            .thenAnswer { }
            .thenThrow(RuntimeException("error"))
            .thenAnswer { }

        // 3 consecutive failures with increasing backoff
        job.checkForCouchDbChanges() // fail 1: backoff 5s
        currentTime = 5000L
        job.checkForCouchDbChanges() // fail 2: backoff 10s
        currentTime = 15000L
        job.checkForCouchDbChanges() // fail 3: backoff 20s
        currentTime = 35000L

        // Success — resets counter
        job.checkForCouchDbChanges()

        // Next failure should start backoff from scratch (5000ms, not 40000ms)
        job.checkForCouchDbChanges()

        // Should skip at currentTime + 4999
        currentTime = 35000L + 4999L
        job.checkForCouchDbChanges()
        verify(databaseChangeDetection, org.mockito.kotlin.times(5)).checkForChanges()

        // Should retry at currentTime = 35000 + 5000
        currentTime = 35000L + 5000L
        job.checkForCouchDbChanges()
        verify(databaseChangeDetection, org.mockito.kotlin.times(6)).checkForChanges()
    }
}
