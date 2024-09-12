package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.common.WebClientTestBase
import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.error.InternalServerException
import com.aamdigital.aambackendservice.error.InvalidArgumentException
import com.aamdigital.aambackendservice.error.NetworkException
import com.aamdigital.aambackendservice.export.core.ExportTemplate
import com.aamdigital.aambackendservice.export.core.RenderTemplateErrorCode
import com.aamdigital.aambackendservice.export.core.RenderTemplateRequest
import com.aamdigital.aambackendservice.export.core.RenderTemplateUseCase
import com.aamdigital.aambackendservice.export.core.TemplateStorage
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.TextNode
import okhttp3.Headers.Companion.toHeaders
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import okio.source
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.web.reactive.function.client.WebClientRequestException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest


@ExtendWith(MockitoExtension::class)
class DefaultRenderTemplateUseCaseTest : WebClientTestBase() {
    @Mock
    lateinit var templateStorage: TemplateStorage

    private lateinit var service: RenderTemplateUseCase

    override fun setUp() {
        super.setUp()
        reset(templateStorage)

        service = DefaultRenderTemplateUseCase(
            webClient = webClient,
            objectMapper = objectMapper,
            templateStorage = templateStorage,
        )
    }

    @Test
    fun `should return Failure when fetchTemplate returns an error`() {
        // Arrange
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode = TextNode("body-data")
        val exception = InternalServerException(message = "fetchTemplate error", cause = null)

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(Mono.error(exception))

        // Act & Assert
        StepVerifier.create(
            service.execute(
                RenderTemplateRequest(
                    templateRef, bodyData
                )
            )
        ).assertNext {
            assertThat(it).isInstanceOf(UseCaseOutcome.Failure::class.java)
            assertEquals(
                RenderTemplateErrorCode.FETCH_TEMPLATE_FAILED_ERROR, (it as UseCaseOutcome.Failure).errorCode
            )
        }.verifyComplete()
    }

    @Test
    fun `should return Failure when json response could not be parsed`() {
        // given
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode = TextNode("body-data")
        val exportTemplate = ExportTemplate(
            id = "export-id",
            templateId = "export-template-id",
            title = "export-title",
            description = "export-description",
            applicableForEntityTypes = emptyList()
        )

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(Mono.just(exportTemplate))
        mockWebServer.enqueue(MockResponse().setBody("invalid json"))

        // when
        StepVerifier.create(
            service.execute(
                RenderTemplateRequest(
                    templateRef, bodyData
                )
            )
        ).assertNext {
            // then
            assertThat(it).isInstanceOf(UseCaseOutcome.Failure::class.java)
            assertEquals(
                RenderTemplateErrorCode.PARSE_RESPONSE_ERROR, (it as UseCaseOutcome.Failure).errorCode
            )
        }.verifyComplete()
    }

    @Test
    fun `should return Failure when fetchTemplateRequest throws exception`() {
        // given
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode = TextNode("body-data")

        whenever(templateStorage.fetchTemplate(templateRef)).thenAnswer {
            throw InvalidArgumentException()
        }

        // when
        StepVerifier.create(
            service.execute(
                RenderTemplateRequest(
                    templateRef, bodyData
                )
            )
        ).assertNext {
            // then
            assertThat(it).isInstanceOf(UseCaseOutcome.Failure::class.java)
            assertEquals(
                RenderTemplateErrorCode.INTERNAL_SERVER_ERROR, (it as UseCaseOutcome.Failure).errorCode
            )
        }.verifyComplete()
    }

    @Test
    fun `should return Failure when fetchTemplateRequest returns Mono error`() {
        // given
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode = TextNode("body-data")

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(Mono.error(RuntimeException()))

        // when
        StepVerifier.create(
            service.execute(
                RenderTemplateRequest(
                    templateRef, bodyData
                )
            )
        ).assertNext {
            // then
            assertThat(it).isInstanceOf(UseCaseOutcome.Failure::class.java)
            assertEquals(
                RenderTemplateErrorCode.INTERNAL_SERVER_ERROR, (it as UseCaseOutcome.Failure).errorCode
            )
        }.verifyComplete()
    }

    @Test
    fun `should return Failure when fetchTemplateRequest returns Mono empty`() {
        // given
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode = TextNode("body-data")

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(Mono.empty())

        // when
        StepVerifier.create(
            service.execute(
                RenderTemplateRequest(
                    templateRef, bodyData
                )
            )
        ).assertNext {
            // then
            assertThat(it).isInstanceOf(UseCaseOutcome.Failure::class.java)
            assertEquals(
                RenderTemplateErrorCode.FETCH_TEMPLATE_FAILED_ERROR, (it as UseCaseOutcome.Failure).errorCode
            )
        }.verifyComplete()
    }

    @Test
    fun `should return Success with DataBuffer`() {
        // given
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode = TextNode("body-data")
        val exportTemplate = ExportTemplate(
            id = "export-id",
            templateId = "export-template-id",
            title = "export-title",
            description = "export-description",
            applicableForEntityTypes = emptyList()
        )

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(Mono.just(exportTemplate))

        mockWebServer.enqueue(
            MockResponse().setBody(
                """
            {
                "success": true,
                "data": {
                    "renderId": "some-render-id"
                }
            }
                """.trimIndent()
            )
        )

        val buffer = Buffer()
        buffer.writeAll(File("src/test/resources/files/pdf-test-file-1.pdf").source())

        mockWebServer.enqueue(
            MockResponse()
                .setHeaders(
                    mapOf(
                        "Content-Type" to "application/pdf",
                        "Content-Length" to buffer.size.toString(),
                        "Cache-Control" to "no-cache, no-store, max-age=0, must-revalidate",
                    ).toHeaders()
                )
                .setBody(buffer)

        )

        StepVerifier.create(
            service.execute(
                RenderTemplateRequest(
                    templateRef, bodyData
                )
            )
        )
            .consumeNextWith { response ->
                // then
                assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

                assertThat((response as UseCaseOutcome.Success).outcome.file).isInstanceOf(DataBuffer::class.java)
            }
            .verifyComplete()
    }

    @Test
    fun `should return Failure with parsed error message`() {
        // given
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode = TextNode("body-data")
        val exportTemplate = ExportTemplate(
            id = "export-id",
            templateId = "export-template-id",
            title = "export-title",
            description = "export-description",
            applicableForEntityTypes = emptyList()
        )

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(Mono.just(exportTemplate))

        mockWebServer.enqueue(
            MockResponse().setBody(
                """
            {
                "success": false,
                "error": "this is an error message"
            }
                """.trimIndent()
            )
        )

        StepVerifier.create(
            service.execute(
                RenderTemplateRequest(
                    templateRef, bodyData
                )
            )
        )
            .consumeNextWith { response ->
                // then
                assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)

                assertEquals("this is an error message", (response as UseCaseOutcome.Failure).errorMessage)
            }
            .verifyComplete()
    }

    @Test
    fun `should return Failure when createRenderRequest returns no response`() {
        // given
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode = TextNode("body-data")
        val exportTemplate = ExportTemplate(
            id = "export-id",
            templateId = "export-template-id",
            title = "export-title",
            description = "export-description",
            applicableForEntityTypes = emptyList()
        )

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(Mono.just(exportTemplate))

        StepVerifier.create(
            service.execute(
                RenderTemplateRequest(
                    templateRef, bodyData
                )
            )
        )
            .consumeNextWith { response ->
                // then
                assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)

                assertThat((response as UseCaseOutcome.Failure).cause)
                    .isInstanceOf(WebClientRequestException::class.java)

                assertThat(response.cause?.cause)
                    .isInstanceOf(NetworkException::class.java)

                assertThat(response.errorCode)
                    .isEqualTo(RenderTemplateErrorCode.CREATE_RENDER_REQUEST_FAILED_ERROR)
            }
            .verifyComplete()
    }

    @Test
    fun `should return Failure when fetchRenderIdRequest fails`() {
        // given
        val templateRef = DomainReference("some-id")
        val bodyData: JsonNode = TextNode("body-data")
        val exportTemplate = ExportTemplate(
            id = "export-id",
            templateId = "export-template-id",
            title = "export-title",
            description = "export-description",
            applicableForEntityTypes = emptyList()
        )

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(Mono.just(exportTemplate))

        mockWebServer.enqueue(
            MockResponse().setBody(
                """
            {
                "success": true,
                "data": {
                    "renderId": "some-render-id"
                }
            }
                """.trimIndent()
            )
        )

        StepVerifier.create(
            service.execute(
                RenderTemplateRequest(
                    templateRef, bodyData
                )
            )
        )
            .consumeNextWith { response ->
                // then
                assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)

                assertThat((response as UseCaseOutcome.Failure).cause)
                    .isInstanceOf(WebClientRequestException::class.java)

                assertThat(response.cause?.cause)
                    .isInstanceOf(NetworkException::class.java)

                assertThat(response.errorCode)
                    .isEqualTo(RenderTemplateErrorCode.FETCH_RENDER_ID_REQUEST_FAILED_ERROR)
            }
            .verifyComplete()
    }

    private fun getHash(byteArray: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(byteArray)).toString(16).padStart(32, '0')
    }
}
