package com.aamdigital.aambackendservice.skill.job

import com.aamdigital.aambackendservice.common.scheduling.ScheduledJobBackoff
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesUseCase
import com.aamdigital.aambackendservice.skill.di.SkillLabApiClientConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class SyncSkillsJobTest {

    private lateinit var job: SyncSkillsJob

    @Mock
    lateinit var fetchUserProfileUpdatesUseCase: FetchUserProfileUpdatesUseCase

    private var currentTime: Long = 0L

    private val skillLabApiClientConfiguration = SkillLabApiClientConfiguration(
        basePath = "https://example.com",
        apiKey = "test-key",
        projectId = "test-project",
    )

    @BeforeEach
    fun setUp() {
        reset(fetchUserProfileUpdatesUseCase)
        currentTime = 0L
        job = SyncSkillsJob(
            skillLabFetchUserProfileUpdatesUseCase = fetchUserProfileUpdatesUseCase,
            skillLabApiClientConfiguration = skillLabApiClientConfiguration,
        )
        job.backoff.clock = { currentTime }
    }

    @Test
    fun `should delegate to use case on each invocation`() {
        job.checkForSkillLabChanges()

        verify(fetchUserProfileUpdatesUseCase).run(any())
    }

    @Test
    fun `should skip execution during backoff period and resume after`() {
        doThrow(RuntimeException("error")).whenever(fetchUserProfileUpdatesUseCase).run(any())

        // First failure sets nextRetryAt = currentTime + 5000
        job.checkForSkillLabChanges()
        verify(fetchUserProfileUpdatesUseCase, times(1)).run(any())

        // Still within backoff — should be skipped
        currentTime = 4999L
        job.checkForSkillLabChanges()
        verify(fetchUserProfileUpdatesUseCase, times(1)).run(any())

        // Backoff elapsed — should retry
        currentTime = 5000L
        job.checkForSkillLabChanges()
        verify(fetchUserProfileUpdatesUseCase, times(2)).run(any())
    }

    @Test
    fun `should increase backoff delay on consecutive failures`() {
        doThrow(RuntimeException("error")).whenever(fetchUserProfileUpdatesUseCase).run(any())

        // 1st failure: backoff = 5000ms
        job.checkForSkillLabChanges()

        // Advance past first backoff
        currentTime = 5000L
        // 2nd failure: backoff = 10000ms
        job.checkForSkillLabChanges()

        // Advance past second backoff
        currentTime = 15000L
        // 3rd failure: backoff = 20000ms
        job.checkForSkillLabChanges()

        // At 34999ms — still within third backoff (15000 + 20000 = 35000)
        currentTime = 34999L
        job.checkForSkillLabChanges()
        verify(fetchUserProfileUpdatesUseCase, times(3)).run(any())

        // At 35000ms — backoff elapsed, should retry
        currentTime = 35000L
        job.checkForSkillLabChanges()
        verify(fetchUserProfileUpdatesUseCase, times(4)).run(any())
    }

    @Test
    fun `should cap backoff delay at 24 hours`() {
        doThrow(RuntimeException("error")).whenever(fetchUserProfileUpdatesUseCase).run(any())

        // Trigger enough failures to exceed 24h cap
        repeat(20) {
            job.checkForSkillLabChanges()
            currentTime += ScheduledJobBackoff.MAX_BACKOFF_MS
        }

        // After 20 failures, next backoff should be capped at MAX_BACKOFF_MS
        val timeBefore = currentTime
        job.checkForSkillLabChanges()

        // Verify the job resumes at exactly timeBefore + MAX_BACKOFF_MS
        currentTime = timeBefore + ScheduledJobBackoff.MAX_BACKOFF_MS
        job.checkForSkillLabChanges()
    }

    @Test
    fun `should reset backoff on success after failures`() {
        whenever(fetchUserProfileUpdatesUseCase.run(any()))
            .thenThrow(RuntimeException("error"))
            .thenThrow(RuntimeException("error"))
            .thenThrow(RuntimeException("error"))
            .thenAnswer { null }
            .thenThrow(RuntimeException("error"))
            .thenAnswer { null }

        // 3 consecutive failures with increasing backoff
        job.checkForSkillLabChanges() // fail 1: backoff 5s
        currentTime = 5000L
        job.checkForSkillLabChanges() // fail 2: backoff 10s
        currentTime = 15000L
        job.checkForSkillLabChanges() // fail 3: backoff 20s
        currentTime = 35000L

        // Success — resets counter
        job.checkForSkillLabChanges()

        // Next failure should start backoff from scratch (5000ms, not 40000ms)
        job.checkForSkillLabChanges()

        // Should skip at currentTime + 4999
        currentTime = 35000L + 4999L
        job.checkForSkillLabChanges()
        verify(fetchUserProfileUpdatesUseCase, times(5)).run(any())

        // Should retry at currentTime = 35000 + 5000
        currentTime = 35000L + 5000L
        job.checkForSkillLabChanges()
        verify(fetchUserProfileUpdatesUseCase, times(6)).run(any())
    }
}
