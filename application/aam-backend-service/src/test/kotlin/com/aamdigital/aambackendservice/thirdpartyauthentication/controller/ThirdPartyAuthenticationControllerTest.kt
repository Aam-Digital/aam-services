package com.aamdigital.aambackendservice.thirdpartyauthentication.controller

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import com.aamdigital.aambackendservice.common.domain.ApplicationConfig
import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseData
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.thirdpartyauthentication.CreateSessionUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.SessionRedirectUseCase
import com.aamdigital.aambackendservice.thirdpartyauthentication.VerifySessionUseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import java.security.Principal

class ThirdPartyAuthenticationControllerTest {
    private val createSessionUseCase = mock<CreateSessionUseCase>()
    private val verifySessionUseCase = mock<VerifySessionUseCase>()
    private val sessionRedirectUseCase = mock<SessionRedirectUseCase>()

    private lateinit var controller: ThirdPartyAuthenticationController

    private lateinit var logger: Logger
    private lateinit var logAppender: ListAppender<ILoggingEvent>

    companion object {
        private const val SESSION_ID = "session-1"
        private const val SESSION_TOKEN = "one-time-token"
    }

    @BeforeEach
    fun setUp() {
        controller =
            ThirdPartyAuthenticationController(
                createSessionUseCase = createSessionUseCase,
                verifySessionUseCase = verifySessionUseCase,
                sessionRedirectUseCase = sessionRedirectUseCase,
                applicationConfig = ApplicationConfig(baseUrl = "https://app.example.org")
            )

        logger = LoggerFactory.getLogger(ThirdPartyAuthenticationController::class.java) as Logger
        logAppender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(logAppender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(logAppender)
    }

    private fun <D : UseCaseData> failure(cause: Throwable) =
        UseCaseOutcome.Failure<D>(
            errorCode = DomainUseCase.DomainError.UNHANDLED_EXCEPTION_IN_USE_CASE,
            errorMessage = "session store unreachable",
            cause = cause
        )

    private fun loggedWarning(): ILoggingEvent {
        val warnings = logAppender.list.filter { it.level == Level.WARN }
        assertThat(warnings).hasSize(1)
        return warnings.single()
    }

    @Test
    fun `should log a failed session creation with its error code and cause`() {
        // Given
        val cause = IllegalStateException("session store unreachable")
        whenever(createSessionUseCase.run(any())).thenReturn(failure(cause))

        // When
        val response =
            controller.startSession(
                UserSessionRequest(
                    userId = "user-1",
                    firstName = "Ada",
                    lastName = "Lovelace",
                    email = "ada@example.org"
                )
            )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        val warning = loggedWarning()
        assertThat(warning.formattedMessage)
            .contains("UNHANDLED_EXCEPTION_IN_USE_CASE", "session store unreachable")
        assertThat((warning.throwableProxy as ThrowableProxy).throwable).isSameAs(cause)
    }

    @Test
    fun `should log a failed session validation with its error code and cause but not the token`() {
        // Given
        val cause = IllegalStateException("session store unreachable")
        whenever(verifySessionUseCase.run(any())).thenReturn(failure(cause))

        // When
        val response = controller.getSession(sessionId = SESSION_ID, sessionToken = SESSION_TOKEN)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        val warning = loggedWarning()
        assertThat(warning.formattedMessage)
            .contains(SESSION_ID, "UNHANDLED_EXCEPTION_IN_USE_CASE", "session store unreachable")
            .doesNotContain(SESSION_TOKEN)
        assertThat((warning.throwableProxy as ThrowableProxy).throwable).isSameAs(cause)
    }

    @Test
    fun `should log a failed session redirect with its error code and cause`() {
        // Given
        val cause = IllegalStateException("session store unreachable")
        whenever(sessionRedirectUseCase.run(any())).thenReturn(failure(cause))

        // When
        val response = controller.getSessionRedirect(sessionId = SESSION_ID, principal = Principal { "user-1" })

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        val warning = loggedWarning()
        assertThat(warning.formattedMessage)
            .contains(SESSION_ID, "UNHANDLED_EXCEPTION_IN_USE_CASE", "session store unreachable")
        assertThat((warning.throwableProxy as ThrowableProxy).throwable).isSameAs(cause)
    }
}
