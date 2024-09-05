package com.aamdigital.aambackendservice.export.controller

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.export.core.CreateTemplateRequest
import com.aamdigital.aambackendservice.export.core.CreateTemplateUseCase
import com.aamdigital.aambackendservice.export.core.RenderTemplateUseCase
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono


/**
 * @param templateId The external identifier of the implementing TemplateEngine
 */
data class CreateTemplateResponseDto(
    val templateId: String,
)

@RestController
@RequestMapping("/v1/export")
@Validated
class TemplateExportController(
    @Qualifier("aam-render-api-client") val webClient: WebClient,
    val createTemplateUseCase: CreateTemplateUseCase,
    val renderTemplateUseCase: RenderTemplateUseCase,
) {

    @GetMapping("/status")
    fun getStatus(): Mono<String> {
        return webClient.get().uri("/status").exchangeToMono {
            it.bodyToMono()
        }
    }

    @PostMapping("/template")
    fun postTemplate(
        @RequestPart("template") file: FilePart
    ): Mono<CreateTemplateResponseDto> {
        return createTemplateUseCase.createTemplate(
            CreateTemplateRequest(
                file = file
            )
        ).map { createTemplateResponse ->
            CreateTemplateResponseDto(
                templateId = createTemplateResponse.template.id
            )
        }
    }

    @PostMapping("/render/{templateId}", produces = [MediaType.APPLICATION_PDF_VALUE])
    fun getTemplate(
        @PathVariable templateId: String,
        @RequestBody templateData: JsonNode,
    ): Mono<DataBuffer> {
        return renderTemplateUseCase.renderTemplate(
            templateRef = DomainReference(templateId),
            bodyData = templateData
        )
    }
}
