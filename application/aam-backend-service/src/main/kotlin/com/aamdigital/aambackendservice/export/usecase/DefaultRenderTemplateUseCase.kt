package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.ExternalSystemException
import com.aamdigital.aambackendservice.export.core.RenderTemplateUseCase
import com.aamdigital.aambackendservice.export.core.TemplateStorage
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

data class RenderRequestResponseDto(
    val success: Boolean,
    val data: RenderRequestResponseDataDto,
)

data class RenderRequestResponseDataDto(
    val renderId: String,
)

class DefaultRenderTemplateUseCase(
    private val webClient: WebClient,
    private val objectMapper: ObjectMapper,
    private val templateStorage: TemplateStorage,
) : RenderTemplateUseCase {
    override fun renderTemplate(templateRef: DomainReference, bodyData: JsonNode): Mono<DataBuffer> {
        return templateStorage.fetchTemplate(templateRef)
            .map { template ->
                template.templateId
            }
            .flatMap { templateId ->
                webClient.post()
                    .uri("/render/$templateId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(bodyData))
                    .exchangeToMono {
                        it.bodyToMono(String::class.java)
                    }
                    .map {
                        parseRenderRequestResponse(it)
                    }
                    .flatMap { renderId ->
                        webClient.get()
                            .uri("/render/$renderId")
                            .accept(MediaType.APPLICATION_JSON)
                            .exchangeToMono {
                                it.bodyToMono(DataBuffer::class.java)
                            }
                    }
            }
    }

    private fun parseRenderRequestResponse(raw: String): String {
        try {
            val renderApiClientResponse = objectMapper.readValue(raw, RenderRequestResponseDto::class.java)
            return renderApiClientResponse.data.renderId
        } catch (e: Exception) {
            throw ExternalSystemException("Could not parse renderId from aam-render-api-client", e)
        }
    }
}
