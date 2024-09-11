package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.common.WebClientTestBase
import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.export.core.CreateTemplateErrorCode
import com.aamdigital.aambackendservice.export.core.CreateTemplateRequest
import com.aamdigital.aambackendservice.export.core.CreateTemplateUseCase
import okhttp3.mockwebserver.MockResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.codec.multipart.FilePart
import org.springframework.util.LinkedMultiValueMap
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.nio.file.Path

class FilePartTestImpl(val name: String) : FilePart {
    override fun name(): String = name
    override fun headers(): HttpHeaders = HttpHeaders.readOnlyHttpHeaders(LinkedMultiValueMap())
    override fun content(): Flux<DataBuffer> = Flux.empty()
    override fun filename(): String = "$name.file"
    override fun transferTo(dest: Path): Mono<Void> = Mono.empty()
}

@ExtendWith(MockitoExtension::class)
class DefaultCreateTemplateUseCaseTest : WebClientTestBase() {

    private lateinit var service: CreateTemplateUseCase

    override fun setUp() {
        super.setUp()
        service = DefaultCreateTemplateUseCase(
            webClient = webClient,
            objectMapper = objectMapper,
        )
    }

    @Test
    fun `should return Failure when json response could not be parsed`() {
        // given
        mockWebServer.enqueue(MockResponse().setBody("invalid json"))

        // when
        StepVerifier.create(
            service.execute(
                CreateTemplateRequest(
                    file = FilePartTestImpl("test"),
                )
            )
        ).assertNext {
            // then
            assertThat(it).isInstanceOf(UseCaseOutcome.Failure::class.java)
            assertEquals(
                CreateTemplateErrorCode.PARSE_RESPONSE_ERROR,
                (it as UseCaseOutcome.Failure).errorCode
            )
        }.verifyComplete()
    }

    @Test
    fun `should return Failure when request returns Mono error`() {
        // given

        // when
        StepVerifier.create(
            service.execute(
                CreateTemplateRequest(
                    file = FilePartTestImpl("test"),
                )
            )
        ).assertNext {
            // then
            assertThat(it).isInstanceOf(UseCaseOutcome.Failure::class.java)
            assertEquals(
                CreateTemplateErrorCode.CREATE_TEMPLATE_REQUEST_FAILED_ERROR,
                (it as UseCaseOutcome.Failure).errorCode
            )
        }.verifyComplete()
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
        StepVerifier.create(
            service.execute(
                CreateTemplateRequest(
                    file = FilePartTestImpl("test"),
                )
            )
        ).assertNext {
            // then
            assertThat(it).isInstanceOf(UseCaseOutcome.Success::class.java)
            assertEquals(
                "template-id",
                (it as UseCaseOutcome.Success).outcome.templateRef.id
            )
        }.verifyComplete()
    }
}
