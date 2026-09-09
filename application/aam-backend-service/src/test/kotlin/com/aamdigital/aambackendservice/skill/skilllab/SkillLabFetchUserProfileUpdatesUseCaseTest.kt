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
import com.aamdigital.aambackendservice.skill.repository.SkillUserProfile
import com.aamdigital.aambackendservice.skill.repository.SkillUserProfileRepository
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
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@ExtendWith(MockitoExtension::class)
class SkillLabFetchUserProfileUpdatesUseCaseTest {
    private lateinit var service: SkillLabFetchUserProfileUpdatesUseCase

    @Mock
    lateinit var skillLabClient: SkillLabClient

    @Mock
    lateinit var skillUserProfileRepository: SkillUserProfileRepository

    @Mock
    lateinit var userProfileUpdatePublisher: UserProfileUpdatePublisher

    @BeforeEach
    fun setup() {
        reset(
            skillLabClient,
            skillUserProfileRepository,
            userProfileUpdatePublisher
        )
        service =
            SkillLabFetchUserProfileUpdatesUseCase(
                skillLabClient = skillLabClient,
                skillUserProfileRepository = skillUserProfileRepository,
                userProfileUpdatePublisher = userProfileUpdatePublisher
            )
    }

    @Test
    fun `should return Failure when SkillLabClient throws exception`() {
        // given
        whenever(skillLabClient.fetchUserProfiles(any(), any(), anyOrNull()))
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
    fun `should publish UserProfileUpdateEvent for each UserProfile fetched from skillLabClient`() {
        // given
        `when`(skillLabClient.fetchUserProfiles(eq(1), eq(50), anyOrNull())).thenReturn(
            listOf(
                DomainReference("user-profile-1"),
                DomainReference("user-profile-2"),
                DomainReference("user-profile-3")
            )
        )

        whenever(userProfileUpdatePublisher.publish(any(), any())).thenReturn(
            getQueueMessage()
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
            userProfileUpdatePublisher,
            times(1)
        ).publish(
            eq(UserProfileUpdateEventQueueConfiguration.USER_PROFILE_UPDATE_QUEUE),
            eq(
                UserProfileUpdateEvent(
                    projectId = "1",
                    userProfileId = "user-profile-1"
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
                    userProfileId = "user-profile-2"
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
                    userProfileId = "user-profile-3"
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
        val response =
            service.run(
                FetchUserProfileUpdatesRequest(
                    projectId = "1"
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
        val response =
            service.run(
                FetchUserProfileUpdatesRequest(
                    projectId = "1"
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
    fun `derives updatedFrom from the newest stored profile`() {
        // given
        val newest = Instant.parse("2024-06-01T10:00:00Z")
        whenever(skillUserProfileRepository.findAll()).thenReturn(
            listOf(
                storedProfile("user-profile-1", Instant.parse("2024-01-01T00:00:00Z")),
                storedProfile("user-profile-2", newest),
                storedProfile("user-profile-3", null)
            )
        )
        `when`(skillLabClient.fetchUserProfiles(eq(1), eq(50), anyOrNull())).thenReturn(emptyList())

        // when
        val response = service.run(FetchUserProfileUpdatesRequest(projectId = "1"))

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)
        verify(skillLabClient, times(1)).fetchUserProfiles(eq(1), eq(50), eq(newest.toString()))
    }

    @Test
    fun `requests a full sync when nothing is stored yet`() {
        // given
        whenever(skillUserProfileRepository.findAll()).thenReturn(emptyList())
        `when`(skillLabClient.fetchUserProfiles(eq(1), eq(50), anyOrNull())).thenReturn(emptyList())

        // when
        val response = service.run(FetchUserProfileUpdatesRequest(projectId = "1"))

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)
        verify(skillLabClient, times(1)).fetchUserProfiles(eq(1), eq(50), eq(null))
    }

    @Test
    fun `a full sync request ignores the derived cursor`() {
        // given
        `when`(skillLabClient.fetchUserProfiles(eq(1), eq(50), anyOrNull())).thenReturn(emptyList())

        // when
        val response = service.run(FetchUserProfileUpdatesRequest(projectId = "1", fullSync = true))

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)
        verify(skillLabClient, times(1)).fetchUserProfiles(eq(1), eq(50), eq(null))
        verify(skillUserProfileRepository, times(0)).findAll()
    }

    @Test
    fun `an explicit updatedFrom overrides the derived cursor`() {
        // given
        val explicit = Instant.parse("2023-03-03T03:03:03Z")
        `when`(skillLabClient.fetchUserProfiles(eq(1), eq(50), anyOrNull())).thenReturn(emptyList())

        // when
        val response =
            service.run(FetchUserProfileUpdatesRequest(projectId = "1", updatedFrom = explicit))

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)
        verify(skillLabClient, times(1)).fetchUserProfiles(eq(1), eq(50), eq(explicit.toString()))
        verify(skillUserProfileRepository, times(0)).findAll()
    }

    private fun storedProfile(
        id: String,
        latestSyncAt: Instant?
    ) = SkillUserProfile(
        externalIdentifier = id,
        fullName = null,
        mobileNumber = null,
        email = null,
        updatedAt = null,
        latestSyncAt = latestSyncAt
    )

    private fun getQueueMessage(): QueueMessage =
        QueueMessage(
            id = UUID.fromString("00000000-0000-0000-0000-000000000000"),
            eventType = "FOO",
            event =
                UserProfileUpdateEvent(
                    projectId = "1",
                    userProfileId = "mock"
                ),
            createdAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        )
}
