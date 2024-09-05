package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.ExternalSystemException
import com.aamdigital.aambackendservice.export.core.CreateTemplateRequest
import com.aamdigital.aambackendservice.export.core.CreateTemplateResponse
import com.aamdigital.aambackendservice.export.core.CreateTemplateUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

data class CreateTemplateResponseDto(
    val success: Boolean,
    val data: CreateTemplateResponseDataDto
)

data class CreateTemplateResponseDataDto(
    val templateId: String,
)

/**
 * Will create a template in our aam-central template export engine (pdf service) and return the
 * external Identifier.
 * The ExportTemplate entity is then created in the frontend.
 */
class DefaultCreateTemplateUseCase(
    private val webClient: WebClient,
    private val objectMapper: ObjectMapper,
) : CreateTemplateUseCase {
    override fun createTemplate(createTemplateRequest: CreateTemplateRequest): Mono<CreateTemplateResponse> {
        val builder = MultipartBodyBuilder()

        builder
            .part("template", createTemplateRequest.file)
            .filename(createTemplateRequest.file.filename())

        return webClient.post()
            .uri("/template")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .accept(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .exchangeToMono {
                it.bodyToMono(String::class.java)
            }
            .map {
                parseResponse(it)
            }
    }

    private fun parseResponse(raw: String): CreateTemplateResponse {
        try {
            val renderApiClientResponse = objectMapper.readValue(raw, CreateTemplateResponseDto::class.java)
            return CreateTemplateResponse(
                template = DomainReference(renderApiClientResponse.data.templateId)
            )
        } catch (e: Exception) {
            throw ExternalSystemException("Could not parse templateId from aam-render-api-client", e)
        }
    }
}
