package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.common.WebClientTestBase
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.export.core.CreateTemplateError
import com.aamdigital.aambackendservice.export.core.CreateTemplateRequest
import com.aamdigital.aambackendservice.export.core.CreateTemplateUseCase
import okhttp3.mockwebserver.MockResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mock.web.MockMultipartFile

@ExtendWith(MockitoExtension::class)
class DefaultCreateTemplateUseCaseTest : WebClientTestBase() {

    private lateinit var service: CreateTemplateUseCase

    override fun setUp() {
        super.setUp()
        service = DefaultCreateTemplateUseCase(
            restClient = restClient,
            objectMapper = objectMapper,
        )
    }

    @Test
    fun `should return Failure when json response could not be parsed`() {
        // given
        mockWebServer.enqueue(MockResponse().setBody("invalid json"))

        // when
        val response = service.run(
            CreateTemplateRequest(
                file = MockMultipartFile("test", "dummy-content".byteInputStream()),
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        assertEquals(
            CreateTemplateError.PARSE_RESPONSE_ERROR,
            (response as UseCaseOutcome.Failure).errorCode
        )
    }

    @Test
    fun `should return Failure when request returns Mono error`() {
        // given

        // when
        val response = service.run(
            CreateTemplateRequest(
                file = MockMultipartFile("test", "dummy-content".byteInputStream()),
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        assertEquals(
            CreateTemplateError.CREATE_TEMPLATE_REQUEST_FAILED_ERROR,
            (response as UseCaseOutcome.Failure).errorCode
        )
    }

    @Test
    fun `should return Success when request returns valid response`() {
        // given
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
            {
                "success": "true",
                "data": {
                    "templateId": "template-id"
                }
            }
        """.trimIndent()
            )
        )

        // when
        val response = service.run(
            CreateTemplateRequest(
                file = MockMultipartFile("test", "dummy-content".byteInputStream()),
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)
        assertEquals(
            "template-id",
            (response as UseCaseOutcome.Success).data.templateRef.id
        )
    }
}
