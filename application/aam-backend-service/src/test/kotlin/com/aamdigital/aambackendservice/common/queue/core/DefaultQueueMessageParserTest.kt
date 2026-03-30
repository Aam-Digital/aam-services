package com.aamdigital.aambackendservice.common.queue.core

import com.aamdigital.aambackendservice.common.changes.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DefaultQueueMessageParserTest {
    private val parser = DefaultQueueMessageParser(ObjectMapper())

    @Test
    fun `should resolve document change event by canonical name`() {
        val body =
            """
            {
              "eventType": "com.aamdigital.aambackendservice.common.changes.DocumentChangeEvent",
              "event": {}
            }
            """.trimIndent().toByteArray()

        val type = parser.getTypeKClass(body)

        assertThat(type).isEqualTo(DocumentChangeEvent::class)
    }

    @Test
    fun `should resolve document change event by legacy package name`() {
        val body =
            """
            {
              "eventType": "legacy.moved.package.DocumentChangeEvent",
              "event": {}
            }
            """.trimIndent().toByteArray()

        val type = parser.getTypeKClass(body)

        assertThat(type).isEqualTo(DocumentChangeEvent::class)
    }

    @Test
    fun `should throw invalid class name for unknown type`() {
        val body =
            """
            {
              "eventType": "legacy.moved.package.OtherEvent",
              "event": {}
            }
            """.trimIndent().toByteArray()

        assertThatThrownBy { parser.getTypeKClass(body) }
            .isInstanceOf(InvalidArgumentException::class.java)
            .hasMessage("Could not find Class for this type.")
    }
}
