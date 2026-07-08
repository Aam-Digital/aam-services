package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.common.changes.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.TestErrorCode
import com.aamdigital.aambackendservice.common.error.InternalServerException
import com.aamdigital.aambackendservice.common.queue.core.QueueMessageParser
import com.aamdigital.aambackendservice.reporting.report.queue.ReportDocumentChangeEventConsumer
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationChangeUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationDebouncer
import com.aamdigital.aambackendservice.reporting.webhook.Webhook
import com.aamdigital.aambackendservice.reporting.webhook.WebhookAuthentication
import com.aamdigital.aambackendservice.reporting.webhook.WebhookAuthenticationType
import com.aamdigital.aambackendservice.reporting.webhook.WebhookTarget
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookOwner
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookStorage
import com.rabbitmq.client.Channel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
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
    lateinit var reportCalculationDebouncer: ReportCalculationDebouncer

    @Mock
    lateinit var reportCalculationChangeUseCase: ReportCalculationChangeUseCase

    @Mock
    lateinit var identifyAffectedReportsUseCase: IdentifyAffectedReportsUseCase

    @Mock
    lateinit var webhookStorage: WebhookStorage

    @Captor
    lateinit var requestCaptor: ArgumentCaptor<CreateReportCalculationRequest>

    @BeforeEach
    fun setUp() {
        reset(
            messageParser,
            reportCalculationDebouncer,
            reportCalculationChangeUseCase,
            identifyAffectedReportsUseCase,
            webhookStorage
        )

        service =
            ReportDocumentChangeEventConsumer(
                messageParser = messageParser,
                reportCalculationDebouncer = reportCalculationDebouncer,
                reportCalculationChangeUseCase = reportCalculationChangeUseCase,
                identifyAffectedReportsUseCase = identifyAffectedReportsUseCase,
                webhookStorage = webhookStorage
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
        val response =
            assertThrows<AmqpRejectAndDontRequeueException> {
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
        val response =
            assertThrows<AmqpRejectAndDontRequeueException> {
                service.consume(rawMessage, mockMessage, mockChannel)
            }

        // then
        Assertions.assertTrue(response.localizedMessage.startsWith("[NO_USECASE_CONFIGURED]"))
    }

    @Test
    fun `should record debounced calculation trigger only for webhook-subscribed affected reports`() {
        // given
        val rawMessage = "foo"
        val documentChangeEvent =
            DocumentChangeEvent(
                database = "app",
                documentId = "individualSurvey:1",
                rev = "1-abc",
                currentVersion = mapOf<String, String>(),
                previousVersion = mapOf<String, String>(),
                deleted = false
            )

        whenever(messageParser.getTypeKClass(any())).thenAnswer { DocumentChangeEvent::class }
        whenever(messageParser.getPayload(any(), eq(DocumentChangeEvent::class))).thenReturn(documentChangeEvent)
        whenever(identifyAffectedReportsUseCase.analyse(documentChangeEvent))
            .thenReturn(
                listOf(
                    DomainReference("ReportConfig:subscribed-report"),
                    DomainReference("ReportConfig:unsubscribed-report"),
                )
            )
        whenever(webhookStorage.fetchAllWebhooks())
            .thenReturn(
                listOf(
                    Webhook(
                        id = "Webhook:1",
                        label = "test webhook",
                        target = WebhookTarget(method = "POST", url = "https://example.org"),
                        authentication =
                            WebhookAuthentication(
                                type = WebhookAuthenticationType.API_KEY,
                                secret = "secret"
                            ),
                        owner = WebhookOwner(creator = "user"),
                        reportSubscriptions = mutableListOf(DomainReference("ReportConfig:subscribed-report"))
                    )
                )
            )

        // when
        service.consume(rawMessage, mockMessage, mockChannel)

        // then
        verify(reportCalculationDebouncer, times(1)).recordChange(capture(requestCaptor))
        assertThat(requestCaptor.value.report.id).isEqualTo("ReportConfig:subscribed-report")
        assertThat(requestCaptor.value.fromAutomaticChangeDetection).isTrue()
    }
}
