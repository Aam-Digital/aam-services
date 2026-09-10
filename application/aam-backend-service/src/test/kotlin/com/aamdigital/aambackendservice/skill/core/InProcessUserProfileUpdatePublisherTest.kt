package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.skill.core.event.UserProfileUpdateEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class InProcessUserProfileUpdatePublisherTest {
    private enum class TestErrorCode : AamErrorCode { IO_ERROR }

    @Mock
    lateinit var syncUserProfileUseCase: SyncUserProfileUseCase

    @Test
    fun `should hand the event straight to the sync use case`() {
        // Given
        val publisher = InProcessUserProfileUpdatePublisher(syncUserProfileUseCase)

        // When
        publisher.publish(UserProfileUpdateEvent(projectId = "project-1", userProfileId = "profile-1"))

        // Then
        val captor = argumentCaptor<SyncUserProfileRequest>()
        verify(syncUserProfileUseCase).run(captor.capture())
        assertThat(captor.firstValue.userProfile.id).isEqualTo("profile-1")
        assertThat(captor.firstValue.project.id).isEqualTo("project-1")
    }

    @Test
    fun `should not propagate a failed sync so the fetch cursor still advances`() {
        // Given the queue this replaces ignored the use case outcome and acked the message, so one
        // unsyncable profile must not abort the whole SkillLab fetch.
        whenever(syncUserProfileUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Failure(
                    errorCode = TestErrorCode.IO_ERROR,
                    errorMessage = "could not store profile"
                )
            )
        val publisher = InProcessUserProfileUpdatePublisher(syncUserProfileUseCase)

        // When
        publisher.publish(UserProfileUpdateEvent(projectId = "project-1", userProfileId = "profile-1"))

        // Then
        verify(syncUserProfileUseCase).run(any())
    }
}
