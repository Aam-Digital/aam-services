package com.aamdigital.aambackendservice.skill.controller

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesData
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesUseCase
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileSyncEntity
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileSyncRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import java.time.OffsetDateTime
import java.util.Optional

class SkillAdminControllerTest {
    private val fetchUserProfileUpdatesUseCase = mock<FetchUserProfileUpdatesUseCase>()
    private val userProfileSyncRepository = mock<SkillLabUserProfileSyncRepository>()

    private lateinit var controller: SkillAdminController

    private lateinit var logger: Logger
    private lateinit var logAppender: ListAppender<ILoggingEvent>

    companion object {
        private const val PROJECT_ID = "project-1"
    }

    @BeforeEach
    fun setUp() {
        controller =
            SkillAdminController(
                skillLabFetchUserProfileUpdatesUseCase = fetchUserProfileUpdatesUseCase,
                skillLabUserProfileSyncRepository = userProfileSyncRepository
            )
        whenever(userProfileSyncRepository.findByProjectId(PROJECT_ID))
            .thenReturn(
                Optional.of(
                    SkillLabUserProfileSyncEntity(
                        projectId = PROJECT_ID,
                        latestSync = OffsetDateTime.parse("2024-12-03T11:50:00Z")
                    )
                )
            )

        logger = LoggerFactory.getLogger(SkillAdminController::class.java) as Logger
        logAppender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(logAppender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(logAppender)
    }

    @Test
    fun `should log a failed manual sync at WARN with its cause and still answer no content`() {
        // Given
        val cause = IllegalStateException("SkillLab unreachable")
        whenever(fetchUserProfileUpdatesUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Failure(
                    errorCode = DomainUseCase.DomainError.UNHANDLED_EXCEPTION_IN_USE_CASE,
                    errorMessage = "SkillLab unreachable",
                    cause = cause
                )
            )

        // When
        val response = controller.triggerSync(projectId = PROJECT_ID)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        val warnings = logAppender.list.filter { it.level == Level.WARN }
        assertThat(warnings).hasSize(1)
        assertThat(warnings.single().formattedMessage)
            .contains(PROJECT_ID, "UNHANDLED_EXCEPTION_IN_USE_CASE", "SkillLab unreachable")
        assertThat((warnings.single().throwableProxy as ThrowableProxy).throwable).isSameAs(cause)
    }

    @Test
    fun `should not log a warning when the manual sync succeeds`() {
        // Given
        whenever(fetchUserProfileUpdatesUseCase.run(any()))
            .thenReturn(UseCaseOutcome.Success(FetchUserProfileUpdatesData(result = emptyList())))

        // When
        val response = controller.triggerSync(projectId = PROJECT_ID)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        assertThat(logAppender.list.filter { it.level.isGreaterOrEqual(Level.WARN) }).isEmpty()
    }
}
