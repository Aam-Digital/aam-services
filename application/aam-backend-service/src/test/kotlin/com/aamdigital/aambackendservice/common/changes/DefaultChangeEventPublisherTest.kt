package com.aamdigital.aambackendservice.common.changes

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.amqp.AmqpException
import org.springframework.amqp.rabbit.core.RabbitTemplate
import com.aamdigital.aambackendservice.common.error.InternalServerException

@ExtendWith(MockitoExtension::class)
class DefaultChangeEventPublisherTest {

    private lateinit var publisher: DefaultChangeEventPublisher
    private val objectMapper = ObjectMapper()

    @Mock
    lateinit var rabbitTemplate: RabbitTemplate

    @BeforeEach
    fun setUp() {
        reset(rabbitTemplate)
        publisher = DefaultChangeEventPublisher(objectMapper, rabbitTemplate)
    }

    @Test
    fun `should send serialized QueueMessage to the specified exchange`() {
        val event = DocumentChangeEvent(
            database = "app",
            documentId = "Child:1",
            rev = "1-abc",
            currentVersion = mapOf("name" to "Alice"),
            previousVersion = emptyMap<String, Any>(),
            deleted = false
        )

        val result = publisher.publish("document.changes", event)

        verify(rabbitTemplate).convertAndSend(eq("document.changes"), eq(""), any<String>())
        assertThat(result.eventType).isEqualTo(DocumentChangeEvent::class.java.canonicalName)
        assertThat(result.event).isEqualTo(event)
    }

    @Test
    fun `should throw InternalServerException when RabbitMQ is unavailable`() {
        whenever(rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<String>()))
            .thenThrow(AmqpException("connection refused"))

        val event = DocumentChangeEvent(
            database = "app",
            documentId = "Child:1",
            rev = "1-abc",
            currentVersion = emptyMap<String, Any>(),
            previousVersion = emptyMap<String, Any>(),
            deleted = false
        )

        assertThrows<InternalServerException> {
            publisher.publish("document.changes", event)
        }
    }
}
