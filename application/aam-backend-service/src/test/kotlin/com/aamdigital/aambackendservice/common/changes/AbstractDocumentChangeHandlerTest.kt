package com.aamdigital.aambackendservice.common.changes

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test

class AbstractDocumentChangeHandlerTest {
    private val event =
        DocumentChangeEvent(
            database = "app",
            documentId = "Child:1",
            rev = "2-abc",
            currentVersion = emptyMap<String, Any>(),
            previousVersion = emptyMap<String, Any>(),
            deleted = false
        )

    private class RecordingHandler(
        private val failure: Exception? = null
    ) : AbstractDocumentChangeHandler() {
        var received: DocumentChangeEvent? = null

        override fun onChange(event: DocumentChangeEvent) {
            received = event
            failure?.let { throw it }
        }
    }

    private class OverridingHandler : AbstractDocumentChangeHandler() {
        var handled: Exception? = null

        override fun onChange(event: DocumentChangeEvent) = throw IllegalStateException("boom")

        override fun errorHandler(
            event: DocumentChangeEvent,
            ex: Exception
        ) {
            handled = ex
        }
    }

    @Test
    fun `should pass the change to onChange`() {
        val handler = RecordingHandler()

        handler.handle(event)

        assertThat(handler.received).isEqualTo(event)
    }

    @Test
    fun `should not propagate a failure from onChange so the remaining handlers still run`() {
        val handler = RecordingHandler(failure = RuntimeException("couchdb unreachable"))

        assertThatCode { handler.handle(event) }.doesNotThrowAnyException()
    }

    @Test
    fun `should let a subclass replace the error handling`() {
        val handler = OverridingHandler()

        handler.handle(event)

        assertThat(handler.handled).isInstanceOf(IllegalStateException::class.java)
    }
}
