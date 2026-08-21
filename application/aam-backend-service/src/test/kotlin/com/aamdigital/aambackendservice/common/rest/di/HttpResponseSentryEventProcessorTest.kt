package com.aamdigital.aambackendservice.common.rest.di

import com.aamdigital.aambackendservice.Application
import com.aamdigital.aambackendservice.common.rest.di.HttpResponseSentryEventProcessor.Companion.MIME_HEADERS_CLASS
import com.aamdigital.aambackendservice.common.rest.di.HttpResponseSentryEventProcessor.Companion.MIME_HEADERS_FINGERPRINT
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.Mechanism
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider

class HttpResponseSentryEventProcessorTest {
    private val processor = HttpResponseSentryEventProcessor()

    /**
     * Rebuilds what production reported: an NPE whose throw site is inside Tomcat's header table,
     * with the message naming the accessor and the loop variable of the failing frame.
     */
    private fun mimeHeadersFailure(
        message: String,
        method: String
    ): NullPointerException =
        NullPointerException(message).apply {
            stackTrace =
                arrayOf(
                    StackTraceElement(MIME_HEADERS_CLASS, method, "MimeHeaders.java", 319),
                    StackTraceElement(
                        "org.apache.coyote.http11.Http11Processor",
                        "prepareResponse",
                        "Http11Processor.java",
                        939
                    )
                )
        }

    @Test
    fun `is discoverable by the application's component scan`() {
        // Given - Sentry only consults processors that made it into the context, and this one lives
        // in a package that had no beans before, so verify the scan reaches it
        val scanner = ClassPathScanningCandidateComponentProvider(true)

        // When
        val components = scanner.findCandidateComponents(Application::class.java.packageName)

        // Then
        assertThat(components.map { it.beanClassName })
            .contains(HttpResponseSentryEventProcessor::class.java.name)
    }

    @Test
    fun `groups the header table failures that differ only in accessor and loop variable`() {
        // Given - the distinct exception messages production reported for this fault
        val failures =
            listOf(
                mimeHeadersFailure(
                    "Cannot invoke \"org.apache.tomcat.util.http.MimeHeaderField.getName()\" " +
                        "because \"this.headers[i]\" is null",
                    "setValue"
                ),
                mimeHeadersFailure(
                    "Cannot invoke \"org.apache.tomcat.util.http.MimeHeaderField.getName()\" " +
                        "because \"this.headers[n]\" is null",
                    "getValue"
                ),
                mimeHeadersFailure(
                    "Cannot invoke \"org.apache.tomcat.util.http.MimeHeaderField.recycle()\" " +
                        "because \"this.headers[i]\" is null",
                    "recycle"
                )
            )

        // When
        val fingerprints = failures.map { processor.process(SentryEvent(it), Hint()).fingerprints }

        // Then
        assertThat(fingerprints).containsOnly(listOf(MIME_HEADERS_FINGERPRINT))
    }

    @Test
    fun `groups the failure when it arrives wrapped in the sentry exception mechanism`() {
        // Given - the logback integration wraps the throwable before handing it to the processors
        val failure =
            mimeHeadersFailure(
                "Cannot invoke \"org.apache.tomcat.util.http.MimeHeaderField.getName()\" " +
                    "because \"this.headers[i]\" is null",
                "setValue"
            )
        val event = SentryEvent(ExceptionMechanismException(Mechanism(), failure, Thread.currentThread()))

        // When
        val result = processor.process(event, Hint())

        // Then
        assertThat(result.fingerprints).containsExactly(MIME_HEADERS_FINGERPRINT)
    }

    @Test
    fun `groups the failure when it is reported as the cause of a wrapper`() {
        // Given
        val failure =
            mimeHeadersFailure(
                "Cannot invoke \"org.apache.tomcat.util.http.MimeHeaderField.getName()\" " +
                    "because \"this.headers[i]\" is null",
                "setValue"
            )
        val event = SentryEvent(IllegalStateException("Async processing failed", failure))

        // When
        val result = processor.process(event, Hint())

        // Then
        assertThat(result.fingerprints).containsExactly(MIME_HEADERS_FINGERPRINT)
    }

    @Test
    fun `leaves application NullPointerExceptions untouched`() {
        // Given - an NPE thrown in our own code must keep Sentry's default grouping
        val applicationFailure =
            NullPointerException("Cannot invoke \"String.length()\" because \"name\" is null").apply {
                stackTrace =
                    arrayOf(
                        StackTraceElement(
                            "com.aamdigital.aambackendservice.reporting.report.ReportService",
                            "calculate",
                            "ReportService.kt",
                            42
                        )
                    )
            }

        // When
        val result = processor.process(SentryEvent(applicationFailure), Hint())

        // Then
        assertThat(result.fingerprints).isNull()
    }

    @Test
    fun `leaves events without a throwable untouched`() {
        // Given
        val event = SentryEvent()

        // When
        val result = processor.process(event, Hint())

        // Then
        assertThat(result).isSameAs(event)
        assertThat(result.fingerprints).isNull()
    }
}
