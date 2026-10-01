package com.aamdigital.aambackendservice.skill.skilllab

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.TestErrorCode
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.InternalServerException
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesRequest
import com.aamdigital.aambackendservice.skill.core.SyncUserProfileRequest
import com.aamdigital.aambackendservice.skill.core.SyncUserProfileUseCase
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileSyncEntity
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileSyncRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@ExtendWith(MockitoExtension::class)
class SkillLabFetchUserProfileUpdatesUseCaseTest {
    private lateinit var service: SkillLabFetchUserProfileUpdatesUseCase

    @Mock
    lateinit var skillLabClient: SkillLabClient

    @Mock
    lateinit var skillLabUserProfileSyncRepository: SkillLabUserProfileSyncRepository

    @Mock
    lateinit var syncUserProfileUseCase: SyncUserProfileUseCase

    @BeforeEach
    fun setup() {
        reset(
            skillLabClient,
            skillLabUserProfileSyncRepository,
            syncUserProfileUseCase
        )
        service =
            SkillLabFetchUserProfileUpdatesUseCase(
                skillLabClient = skillLabClient,
                skillLabUserProfileSyncRepository = skillLabUserProfileSyncRepository,
                syncUserProfileUseCase = syncUserProfileUseCase
            )
    }

    @Test
    fun `should return Failure when SkillLabClient throws exception`() {
        // given
        whenever(skillLabClient.fetchUserProfiles(any(), anyOrNull()))
            .thenAnswer {
                throw InternalServerException(
                    message = "error",
                    code = TestErrorCode.TEST_EXCEPTION,
                    cause = null
                )
            }

        // when
        val response =
            service.run(
                FetchUserProfileUpdatesRequest(
                    projectId = "1"
                )
            )

        // then

        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        Assertions.assertEquals(
            SkillLabFetchUserProfileUpdatesErrorCode.EXTERNAL_SYSTEM_ERROR,
            (response as UseCaseOutcome.Failure).errorCode
        )
    }

    @Test
    fun `should sync each UserProfile fetched from skillLabClient`() {
        // given
        `when`(skillLabClient.fetchUserProfiles(eq(Pageable.ofSize(50).withPage(1)), anyOrNull())).thenReturn(
            listOf(
                DomainReference("user-profile-1"),
                DomainReference("user-profile-2"),
                DomainReference("user-profile-3")
            )
        )

        // when
        val response =
            service.run(
                FetchUserProfileUpdatesRequest(
                    projectId = "1"
                )
            )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        listOf("user-profile-1", "user-profile-2", "user-profile-3").forEach { userProfileId ->
            verify(syncUserProfileUseCase, times(1)).run(
                eq(
                    SyncUserProfileRequest(
                        userProfile = DomainReference(userProfileId),
                        project = DomainReference("1")
                    )
                )
            )
        }
    }

    @Test
    fun `should abort fetchNextBatch loop when reaching MAX_RESULTS_LIMIT`() {
        val maxResultsLimit = 10_000
        val pageSize = 50

        // given
        whenever(
            skillLabClient.fetchUserProfiles(
                any(),
                anyOrNull()
            )
        ).thenReturn(
            (1..pageSize).map {
                DomainReference("user-profile-$it")
            }
        )

        // when
        val response =
            service.run(
                FetchUserProfileUpdatesRequest(
                    projectId = "1"
                )
            )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        verify(
            syncUserProfileUseCase,
            times(maxResultsLimit)
        ).run(
            any()
        )
    }

    @Test
    fun `should sync the remaining UserProfiles and advance the cursor when one fails to sync`() {
        // given one profile that cannot be synced must not stall the project-wide sync
        whenever(
            skillLabClient.fetchUserProfiles(
                any(),
                anyOrNull()
            )
        ).thenReturn(
            listOf(
                DomainReference("user-profile-1"),
                DomainReference("user-profile-2")
            )
        )

        whenever(syncUserProfileUseCase.run(any())).thenReturn(
            UseCaseOutcome.Failure(
                errorCode = TestErrorCode.TEST_EXCEPTION,
                errorMessage = "could not store profile"
            )
        )

        // when
        val response =
            service.run(
                FetchUserProfileUpdatesRequest(
                    projectId = "1"
                )
            )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)
        verify(syncUserProfileUseCase, times(2)).run(any())
        verify(skillLabUserProfileSyncRepository, times(1)).save(any())
    }

    @Test
    fun `should store latestSyncEntity when SyncEntity exist for this projectId`() {
        val syncEntity =
            SkillLabUserProfileSyncEntity(
                projectId = "1",
                latestSync = OffsetDateTime.parse("2024-01-01T00:00:00Z")
            )

        // given
        whenever(skillLabUserProfileSyncRepository.findByProjectId(any()))
            .thenReturn(
                Optional.of(
                    syncEntity
                )
            )

        `when`(skillLabClient.fetchUserProfiles(eq(Pageable.ofSize(50).withPage(1)), anyOrNull())).thenReturn(
            listOf(
                DomainReference("user-profile-1"),
                DomainReference("user-profile-2"),
                DomainReference("user-profile-3")
            )
        )

        // when
        val response =
            service.run(
                FetchUserProfileUpdatesRequest(
                    projectId = "1"
                )
            )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        verify(
            skillLabUserProfileSyncRepository,
            times(1)
        ).save(
            eq(syncEntity)
        )
    }

    @Test
    fun `should store latestSyncEntity when no SyncEntity exist for this projectId`() {
        // given
        `when`(skillLabClient.fetchUserProfiles(eq(Pageable.ofSize(50).withPage(1)), anyOrNull())).thenReturn(
            listOf(
                DomainReference("user-profile-1"),
                DomainReference("user-profile-2"),
                DomainReference("user-profile-3")
            )
        )

        // when
        val response =
            service.run(
                FetchUserProfileUpdatesRequest(
                    projectId = "1"
                )
            )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        verify(
            skillLabUserProfileSyncRepository,
            times(1)
        ).save(
            any()
        )
    }

    @Test
    fun `should store the time the sync started, so profiles changed during the fetch are fetched again`() {
        // given
        var fetchedAt: Instant? = null
        whenever(skillLabClient.fetchUserProfiles(any(), anyOrNull())).thenAnswer {
            Thread.sleep(5)
            fetchedAt = Instant.now()
            listOf(DomainReference("user-profile-1"))
        }

        // when
        service.run(FetchUserProfileUpdatesRequest(projectId = "1"))

        // then
        val captor = argumentCaptor<SkillLabUserProfileSyncEntity>()
        verify(skillLabUserProfileSyncRepository).save(captor.capture())
        assertThat(captor.firstValue.latestSync.toInstant()).isBefore(fetchedAt)
    }
}
