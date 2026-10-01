package com.aamdigital.aambackendservice.skill.controller

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.skill.core.SearchUserProfileData
import com.aamdigital.aambackendservice.skill.core.SearchUserProfileUseCase
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus

class SkillControllerTest {
    private val searchUserProfileUseCase = mock<SearchUserProfileUseCase>()

    private lateinit var controller: SkillController

    private lateinit var logger: Logger
    private lateinit var logAppender: ListAppender<ILoggingEvent>

    companion object {
        private const val FULL_NAME = "Ada Lovelace"
        private const val EMAIL = "ada@example.org"
        private const val PHONE = "+49 30 1234567"
    }

    @BeforeEach
    fun setUp() {
        controller =
            SkillController(
                searchUserProfileUseCase = searchUserProfileUseCase,
                userProfileRepository = mock<SkillLabUserProfileRepository>(),
                objectMapper = ObjectMapper()
            )

        logger = LoggerFactory.getLogger(SkillController::class.java) as Logger
        logAppender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(logAppender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(logAppender)
    }

    private fun searchFailsWith(cause: Throwable) {
        whenever(searchUserProfileUseCase.run(any()))
            .thenReturn(
                UseCaseOutcome.Failure<SearchUserProfileData>(
                    errorCode = DomainUseCase.DomainError.UNHANDLED_EXCEPTION_IN_USE_CASE,
                    errorMessage = "profile store unreachable",
                    cause = cause
                )
            )
    }

    @Test
    fun `should log a failed profile search at WARN with its cause`() {
        // Given
        val cause = IllegalStateException("profile store unreachable")
        searchFailsWith(cause)

        // When
        val response = controller.fetchUserProfiles(fullName = FULL_NAME, email = EMAIL, phone = PHONE)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        val warnings = logAppender.list.filter { it.level == Level.WARN }
        assertThat(warnings).hasSize(1)
        assertThat(warnings.single().formattedMessage)
            .contains("UNHANDLED_EXCEPTION_IN_USE_CASE", "profile store unreachable")
        assertThat((warnings.single().throwableProxy as ThrowableProxy).throwable).isSameAs(cause)
    }

    @Test
    fun `should not write the searched name, email or phone number to the log`() {
        // Given
        searchFailsWith(IllegalStateException("profile store unreachable"))

        // When
        controller.fetchUserProfiles(fullName = FULL_NAME, email = EMAIL, phone = PHONE)

        // Then
        assertThat(logAppender.list).isNotEmpty
        assertThat(logAppender.list.map { it.formattedMessage })
            .noneMatch { it.contains(FULL_NAME) || it.contains(EMAIL) || it.contains(PHONE) }
    }
}
