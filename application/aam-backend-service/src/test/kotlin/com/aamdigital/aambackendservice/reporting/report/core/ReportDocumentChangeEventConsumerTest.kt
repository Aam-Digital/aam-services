package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.common.domain.TestErrorCode
import com.aamdigital.aambackendservice.common.error.InternalServerException
import com.aamdigital.aambackendservice.common.queue.core.QueueMessageParser
import com.aamdigital.aambackendservice.reporting.report.queue.ReportDocumentChangeEventConsumer
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationChangeUseCase
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookStorage
import com.rabbitmq.client.Channel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import org.springframework.amqp.AmqpRejectAndDontRequeueException
import org.springframework.amqp.core.Message

@ExtendWith(MockitoExtension::class)
class ReportDocumentChangeEventConsumerTest {

    private lateinit var service: ReportDocumentChangeEventConsumer

    @Mock
    lateinit var messageParser: QueueMessageParser

    @Mock
    lateinit var mockMessage: Message

    @Mock
    lateinit var mockChannel: Channel

    @Mock
    lateinit var createReportCalculationUseCase: CreateReportCalculationUseCase

    @Mock
    lateinit var reportCalculationChangeUseCase: ReportCalculationChangeUseCase

    @Mock
    lateinit var identifyAffectedReportsUseCase: IdentifyAffectedReportsUseCase

    @Mock
    lateinit var webhookStorage: WebhookStorage

    @BeforeEach
    fun setUp() {
        reset(
            messageParser,
            createReportCalculationUseCase,
            reportCalculationChangeUseCase,
            identifyAffectedReportsUseCase,
            webhookStorage,
        )

        service = ReportDocumentChangeEventConsumer(
            messageParser = messageParser,
            createReportCalculationUseCase = createReportCalculationUseCase,
            reportCalculationChangeUseCase = reportCalculationChangeUseCase,
            identifyAffectedReportsUseCase = identifyAffectedReportsUseCase,
            webhookStorage = webhookStorage,
        )
    }

    @Test
    fun `should return MonoError with AmqpRejectAndDontRequeueException when MessageParser throws exception`() {
        // given
        val rawMessage = "foo"

        whenever(messageParser.getTypeKClass(any()))
            .thenAnswer {
                throw InternalServerException(
                    message = "error",
                    code = TestErrorCode.TEST_EXCEPTION,
                    cause = null
                )
            }

        // when
        val response = assertThrows<AmqpRejectAndDontRequeueException> {
            service.consume(rawMessage, mockMessage, mockChannel)
        }

        // then
        Assertions.assertTrue(response.localizedMessage.startsWith("[TEST_EXCEPTION]"))
    }

    @Test
    fun `should return MonoError with AmqpRejectAndDontRequeueException when EventType is unknown`() {
        // given
        val rawMessage = "foo"

        whenever(messageParser.getTypeKClass(any()))
            .thenAnswer {
                String::class
            }

        // when
        val response = assertThrows<AmqpRejectAndDontRequeueException> {
            service.consume(rawMessage, mockMessage, mockChannel)
        }

        // then
        Assertions.assertTrue(response.localizedMessage.startsWith("[NO_USECASE_CONFIGURED]"))
    }
}
