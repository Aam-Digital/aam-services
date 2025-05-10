package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.common.WebClientTestBase
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.InternalServerException
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.TestErrorCode
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.export.core.FetchTemplateError
import com.aamdigital.aambackendservice.export.core.FetchTemplateRequest
import com.aamdigital.aambackendservice.export.core.FetchTemplateUseCase
import com.aamdigital.aambackendservice.export.core.TemplateExport
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
import org.springframework.core.io.ClassPathResource
import java.io.InputStream


@ExtendWith(MockitoExtension::class)
class DefaultFetchTemplateUseCaseTest : WebClientTestBase() {

    @Mock
    lateinit var templateStorage: TemplateStorage

    private lateinit var service: FetchTemplateUseCase

    override fun setUp() {
        super.setUp()
        reset(templateStorage)

        service = DefaultFetchTemplateUseCase(
            restClient = restClient,
            templateStorage = templateStorage
        )
    }

    @Test
    fun `should return Failure when fetchTemplate returns an error`() {
        // given
        val templateRef = DomainReference("some-id")
        val exception = InternalServerException(
            message = "fetchTemplate error",
            code = TestErrorCode.TEST_EXCEPTION,
            cause = null
        )

        whenever(templateStorage.fetchTemplate(templateRef)).thenAnswer {
            throw exception
        }

        // when
        val response = service.run(
            FetchTemplateRequest(
                templateRef
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        assertEquals(
            FetchTemplateError.FETCH_TEMPLATE_REQUEST_FAILED_ERROR, (response as UseCaseOutcome.Failure).errorCode
        )
    }

    @Test
    fun `should return Failure when fetchTemplate returns empty response`() {
        // given
        val templateRef = DomainReference("some-id")
        val exception = InternalServerException(
            message = "fetchTemplate error",
            code = TestErrorCode.TEST_EXCEPTION,
            cause = null
        )

        whenever(templateStorage.fetchTemplate(templateRef)).thenAnswer {
            throw exception
        }

        // when
        val response = service.run(
            FetchTemplateRequest(
                templateRef
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        assertEquals(
            FetchTemplateError.FETCH_TEMPLATE_REQUEST_FAILED_ERROR, (response as UseCaseOutcome.Failure).errorCode
        )
    }

    @Test
    fun `should return Failure when fetchTemplateRequest throws exception`() {
        // given
        val templateRef = DomainReference("some-id")

        val exception = ExternalSystemException(
            message = "fetchTemplate error",
            code = TestErrorCode.TEST_EXCEPTION,
            cause = null
        )

        whenever(templateStorage.fetchTemplate(templateRef)).thenAnswer {
            throw exception
        }

        // when
        val response = service.run(
            FetchTemplateRequest(
                templateRef
            )
        )
        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        assertEquals(
            FetchTemplateError.FETCH_TEMPLATE_REQUEST_FAILED_ERROR, (response as UseCaseOutcome.Failure).errorCode
        )
    }

    @Test
    fun `should return Failure when response could not be processed`() {
        // given
        val templateRef = DomainReference("some-id")

        val templateExport = TemplateExport(
            id = "export-id",
            templateId = "export-template-id",
            targetFileName = "target_file_name.file",
            title = "export-title",
            description = "export-description",
            applicableForEntityTypes = emptyList()
        )

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(templateExport)

        // when
        val response = service.run(
            FetchTemplateRequest(
                templateRef
            )
        )

        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        assertEquals(
            FetchTemplateError.FETCH_TEMPLATE_REQUEST_FAILED_ERROR, (response as UseCaseOutcome.Failure).errorCode
        )
    }

    @Test
    fun `should return Success with InputStream`() {
        // given
        val templateRef = DomainReference("some-id")

        val templateExport = TemplateExport(
            id = "export-id",
            templateId = "export-template-id",
            targetFileName = "target_file_name.file",
            title = "export-title",
            description = "export-description",
            applicableForEntityTypes = emptyList()
        )

        whenever(templateStorage.fetchTemplate(templateRef)).thenReturn(templateExport)

        val buffer = Buffer()
        buffer.writeAll(ClassPathResource("files/docx-test-file-1.docx").file.source())

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

        // when
        val response = service.run(
            FetchTemplateRequest(
                templateRef
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        assertThat((response as UseCaseOutcome.Success).data.file).isInstanceOf(InputStream::class.java)
    }
}
