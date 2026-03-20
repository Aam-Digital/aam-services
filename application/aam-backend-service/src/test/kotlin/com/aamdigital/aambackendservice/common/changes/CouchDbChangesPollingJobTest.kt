package com.aamdigital.aambackendservice.common.changes

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class CouchDbChangesPollingJobTest {

    private lateinit var job: CouchDbChangesPollingJob

    @Mock
    lateinit var databaseChangeDetection: CouchDbChangesProcessor

    @BeforeEach
    fun setUp() {
        reset(databaseChangeDetection)
        job = CouchDbChangesPollingJob(databaseChangeDetection)
    }

    @Test
    fun `should delegate to checkForChanges on each invocation`() {
        job.checkForCouchDbChanges()

        verify(databaseChangeDetection).checkForChanges()
    }

    @Test
    fun `should stop polling after maxErrorCount consecutive failures`() {
        doThrow(RuntimeException("error")).whenever(databaseChangeDetection).checkForChanges()

        // 5 failures to reach maxErrorCount
        repeat(5) { job.checkForCouchDbChanges() }

        // 6th call should be skipped
        job.checkForCouchDbChanges()

        verify(databaseChangeDetection, org.mockito.kotlin.times(5)).checkForChanges()
    }

    @Test
    fun `should not stop when error count reaches threshold-1 and then recovers`() {
        // 4 consecutive failures (one below maxErrorCount of 5), then recovery
        whenever(databaseChangeDetection.checkForChanges())
            .thenThrow(RuntimeException("error"))
            .thenThrow(RuntimeException("error"))
            .thenThrow(RuntimeException("error"))
            .thenThrow(RuntimeException("error"))
            .thenAnswer { }
            .thenThrow(RuntimeException("error"))
            .thenAnswer { }

        repeat(4) { job.checkForCouchDbChanges() } // errorCounter = 4
        job.checkForCouchDbChanges() // succeeds, resets counter to 0
        job.checkForCouchDbChanges() // another error, but after counter reset
        job.checkForCouchDbChanges() // still runs (not blocked)

        verify(databaseChangeDetection, org.mockito.kotlin.times(7)).checkForChanges()
    }
}
