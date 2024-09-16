package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.common.WebClientTestBase
import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.error.InternalServerException
import com.aamdigital.aambackendservice.error.InvalidArgumentException
import com.aamdigital.aambackendservice.export.core.ExportTemplate
import com.aamdigital.aambackendservice.export.core.FetchTemplateErrorCode
import com.aamdigital.aambackendservice.export.core.FetchTemplateRequest
import com.aamdigital.aambackendservice.export.core.FetchTemplateUseCase
import com.aamdigital.aambackendservice.export.core.TemplateStorage
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
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.io.File


@ExtendWith(MockitoExtension::class)
class DefaultFetchTemplateUseCaseTest : WebClientTestBase() {

    @Mock
    lateinit var templateStorage: TemplateStorage

    private lateinit var service: FetchTemplateUseCase

    override fun setUp() {
        super.setUp()
        reset(templateStorage)

        service = DefaultFetchTemplateUseCase(
            webClient = webClient,
            templateStorage = templateStorage
        )
    }

    @Test
    fun `should return Failure when fetchTemplate returns an error`() {
        // Arrange
        val templateRef = DomainReference("some-id")
        val exception = InternalServerException(message = "fetchTemplate error", cause = null)

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(Mono.error(exception))

        // Act & Assert
        StepVerifier.create(
            service.execute(
                FetchTemplateRequest(
                    templateRef
                )
            )
        ).assertNext {
            assertThat(it).isInstanceOf(UseCaseOutcome.Failure::class.java)
            assertEquals(
                FetchTemplateErrorCode.FETCH_TEMPLATE_REQUEST_FAILED_ERROR, (it as UseCaseOutcome.Failure).errorCode
            )
        }.verifyComplete()
    }

    @Test
    fun `should return Failure when fetchTemplate returns empty response`() {
        // Arrange
        val templateRef = DomainReference("some-id")
        val exception = InternalServerException(message = "fetchTemplate error", cause = null)

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(Mono.empty())

        // Act & Assert
        StepVerifier.create(
            service.execute(
                FetchTemplateRequest(
                    templateRef
                )
            )
        ).assertNext {
            assertThat(it).isInstanceOf(UseCaseOutcome.Failure::class.java)
            assertEquals(
                FetchTemplateErrorCode.FETCH_TEMPLATE_REQUEST_FAILED_ERROR, (it as UseCaseOutcome.Failure).errorCode
            )
        }.verifyComplete()
    }

    @Test
    fun `should return Failure when fetchTemplateRequest throws exception`() {
        // given
        val templateRef = DomainReference("some-id")

        whenever(templateStorage.fetchTemplate(templateRef)).thenAnswer {
            throw InvalidArgumentException()
        }

        // when
        StepVerifier.create(
            service.execute(
                FetchTemplateRequest(
                    templateRef
                )
            )
        ).assertNext {
            // then
            assertThat(it).isInstanceOf(UseCaseOutcome.Failure::class.java)
            assertEquals(
                FetchTemplateErrorCode.INTERNAL_SERVER_ERROR, (it as UseCaseOutcome.Failure).errorCode
            )
        }.verifyComplete()
    }

    @Test
    fun `should return Failure when response could not be processed`() {
        // Arrange
        val templateRef = DomainReference("some-id")

        val exportTemplate = ExportTemplate(
            id = "export-id",
            templateId = "export-template-id",
            targetFileName = "target_file_name.file",
            title = "export-title",
            description = "export-description",
            applicableForEntityTypes = emptyList()
        )

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(Mono.just(exportTemplate))

        // Act & Assert
        StepVerifier.create(
            service.execute(
                FetchTemplateRequest(
                    templateRef
                )
            )
        ).assertNext {
            assertThat(it).isInstanceOf(UseCaseOutcome.Failure::class.java)
            assertEquals(
                FetchTemplateErrorCode.FETCH_TEMPLATE_REQUEST_FAILED_ERROR, (it as UseCaseOutcome.Failure).errorCode
            )
        }.verifyComplete()
    }

    @Test
    fun `should return Success with DataBuffer`() {
        // given
        val templateRef = DomainReference("some-id")

        val exportTemplate = ExportTemplate(
            id = "export-id",
            templateId = "export-template-id",
            targetFileName = "target_file_name.file",
            title = "export-title",
            description = "export-description",
            applicableForEntityTypes = emptyList()
        )

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(Mono.just(exportTemplate))

        val buffer = Buffer()
        buffer.writeAll(File("src/test/resources/files/docx-test-file-1.docx").source())

        mockWebServer.enqueue(
            MockResponse()
                .setHeaders(
                    mapOf(
                        "Content-Type" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "Content-Length" to buffer.size.toString(),
                        "Cache-Control" to "no-cache, no-store, max-age=0, must-revalidate",
                    ).toHeaders()
                )
                .setBody(buffer)

        )

        StepVerifier.create(
            service.execute(
                FetchTemplateRequest(
                    templateRef
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
}
