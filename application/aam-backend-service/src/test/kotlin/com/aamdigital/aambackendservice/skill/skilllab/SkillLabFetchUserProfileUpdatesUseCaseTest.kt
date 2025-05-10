package com.aamdigital.aambackendservice.skill.skilllab

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.TestErrorCode
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.InternalServerException
import com.aamdigital.aambackendservice.common.queue.core.QueueMessage
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesRequest
import com.aamdigital.aambackendservice.skill.core.UserProfileUpdatePublisher
import com.aamdigital.aambackendservice.skill.core.event.UserProfileUpdateEvent
import com.aamdigital.aambackendservice.skill.di.UserProfileUpdateEventQueueConfiguration
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileSyncEntity
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileSyncRepository
import okio.IOException
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Pageable
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
    lateinit var userProfileUpdatePublisher: UserProfileUpdatePublisher

    @BeforeEach
    fun setup() {
        reset(
            skillLabClient,
            skillLabUserProfileSyncRepository,
            userProfileUpdatePublisher
        )
        service = SkillLabFetchUserProfileUpdatesUseCase(
            skillLabClient = skillLabClient,
            skillLabUserProfileSyncRepository = skillLabUserProfileSyncRepository,
            userProfileUpdatePublisher = userProfileUpdatePublisher
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
                    cause = null,
                )
            }

        // when
        val response = service.run(
            FetchUserProfileUpdatesRequest(
                projectId = "1",
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
    fun `should publish UserProfileUpdateEvent for each UserProfile fetched from skillLabClient`() {
        // given
        `when`(skillLabClient.fetchUserProfiles(eq(Pageable.ofSize(50).withPage(1)), anyOrNull())).thenReturn(
            listOf(
                DomainReference("user-profile-1"),
                DomainReference("user-profile-2"),
                DomainReference("user-profile-3"),
            )
        )

        whenever(userProfileUpdatePublisher.publish(any(), any())).thenReturn(
            getQueueMessage()
        )

        // when
        val response = service.run(
            FetchUserProfileUpdatesRequest(
                projectId = "1",
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        verify(
            userProfileUpdatePublisher,
            times(1)
        ).publish(
            eq(UserProfileUpdateEventQueueConfiguration.USER_PROFILE_UPDATE_QUEUE),
            eq(
                UserProfileUpdateEvent(
                    projectId = "1",
                    userProfileId = "user-profile-1",
                )
            )
        )

        verify(
            userProfileUpdatePublisher,
            times(1)
        ).publish(
            eq(UserProfileUpdateEventQueueConfiguration.USER_PROFILE_UPDATE_QUEUE),
            eq(
                UserProfileUpdateEvent(
                    projectId = "1",
                    userProfileId = "user-profile-2",
                )
            )
        )

        verify(
            userProfileUpdatePublisher,
            times(1)
        ).publish(
            eq(UserProfileUpdateEventQueueConfiguration.USER_PROFILE_UPDATE_QUEUE),
            eq(
                UserProfileUpdateEvent(
                    projectId = "1",
                    userProfileId = "user-profile-3",
                )
            )
        )
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

        whenever(userProfileUpdatePublisher.publish(any(), any())).thenReturn(
            getQueueMessage()
        )

        // when
        val response = service.run(
            FetchUserProfileUpdatesRequest(
                projectId = "1",
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        verify(
            userProfileUpdatePublisher,
            times(maxResultsLimit)
        ).publish(
            eq(UserProfileUpdateEventQueueConfiguration.USER_PROFILE_UPDATE_QUEUE),
            any()
        )
    }

    @Test
    fun `should return Failure when userProfileUpdatePublisher throws Exception`() {
        // given
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

        whenever(userProfileUpdatePublisher.publish(any(), any())).thenAnswer {
            throw IOException("mock-error")
        }

        // when
        val response = service.run(
            FetchUserProfileUpdatesRequest(
                projectId = "1",
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        Assertions.assertEquals(
            SkillLabFetchUserProfileUpdatesErrorCode.EVENT_PUBLISH_ERROR,
            (response as UseCaseOutcome.Failure).errorCode
        )
        Assertions.assertEquals("mock-error", response.errorMessage)
    }

    @Test
    fun `should store latestSyncEntity when SyncEntity exist for this projectId`() {
        val syncEntity = SkillLabUserProfileSyncEntity(
            id = 42L,
            projectId = "1",
            latestSync = OffsetDateTime.parse("2024-01-01T00:00:00Z"),
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
                DomainReference("user-profile-3"),
            )
        )

        whenever(userProfileUpdatePublisher.publish(any(), any())).thenReturn(
            getQueueMessage()
        )

        // when
        val response = service.run(
            FetchUserProfileUpdatesRequest(
                projectId = "1",
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
                DomainReference("user-profile-3"),
            )
        )

        whenever(userProfileUpdatePublisher.publish(any(), any())).thenReturn(
            getQueueMessage()
        )

        // when
        val response = service.run(
            FetchUserProfileUpdatesRequest(
                projectId = "1",
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

    private fun getQueueMessage(): QueueMessage = QueueMessage(
        id = UUID.fromString("00000000-0000-0000-0000-000000000000"),
        eventType = "FOO",
        event = UserProfileUpdateEvent(
            projectId = "1",
            userProfileId = "mock",
        ),
        createdAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )
}
