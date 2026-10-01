package com.aamdigital.aambackendservice.common.domain

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.InternalServerException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

enum class TestErrorCode : AamErrorCode {
    TEST_EXCEPTION
}

class BasicDomainUseCaseTest {
    class BasicTestUseCase(
        private val exception: Exception =
            InternalServerException(
                message = "error",
                code = TestErrorCode.TEST_EXCEPTION
            )
    ) : DomainUseCase<UseCaseRequest, UseCaseData>() {
        override fun apply(request: UseCaseRequest): UseCaseOutcome<UseCaseData> = throw exception
    }

    private val request: UseCaseRequest = object : UseCaseRequest {}

    private lateinit var logger: Logger
    private lateinit var logAppender: ListAppender<ILoggingEvent>
    private var originalLevel: Level? = null

    @BeforeEach
    fun setUp() {
        logger = LoggerFactory.getLogger(BasicTestUseCase::class.java) as Logger
        logAppender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(logAppender)
        // explicit, so neither a WARN nor the DEBUG line can be filtered out by whatever level an
        // earlier test in this JVM left behind
        originalLevel = logger.level
        logger.level = Level.DEBUG
    }

    @AfterEach
    fun tearDown() {
        logger.level = originalLevel
        logger.detachAppender(logAppender)
    }

    @Test
    fun `should catch exception in UseCaseOutcome when call apply()`() {
        // Given
        val useCase = BasicTestUseCase()

        // When
        val response = useCase.run(request)

        // Then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        assertThat((response as UseCaseOutcome.Failure).errorCode)
            .isEqualTo(TestErrorCode.TEST_EXCEPTION)
    }

    @Test
    fun `should return the thrown exception as the Failure's cause`() {
        // Given
        val exception = IllegalStateException("database unreachable")
        val useCase = BasicTestUseCase(exception)

        // When
        val response = useCase.run(request)

        // Then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        val failure = response as UseCaseOutcome.Failure
        assertThat(failure.errorCode).isEqualTo(DomainUseCase.DomainError.UNHANDLED_EXCEPTION_IN_USE_CASE)
        assertThat(failure.errorMessage).isEqualTo("database unreachable")
        assertThat(failure.cause).isSameAs(exception)
    }

    @Test
    fun `should leave logging the failure to the caller and log nothing at WARN or above`() {
        // Given
        val exception = IllegalStateException("database unreachable")
        val useCase = BasicTestUseCase(exception)

        // When
        useCase.run(request)

        // Then
        assertThat(logAppender.list.filter { it.level.isGreaterOrEqual(Level.WARN) }).isEmpty()
        val debugEvents = logAppender.list.filter { it.level == Level.DEBUG }
        assertThat(debugEvents).hasSize(1)
        assertThat((debugEvents.single().throwableProxy as ThrowableProxy).throwable).isSameAs(exception)
    }

    @Test
    fun `should return a Failure for an exception without a message instead of throwing`() {
        // Given - what Kotlin's !! throws
        val exception = NullPointerException()
        val useCase = BasicTestUseCase(exception)

        // When
        val response = useCase.run(request)

        // Then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        val failure = response as UseCaseOutcome.Failure
        assertThat(failure.errorCode).isEqualTo(DomainUseCase.DomainError.UNHANDLED_EXCEPTION_IN_USE_CASE)
        assertThat(failure.errorMessage).isEqualTo("java.lang.NullPointerException")
        assertThat(failure.cause).isSameAs(exception)
    }
}
