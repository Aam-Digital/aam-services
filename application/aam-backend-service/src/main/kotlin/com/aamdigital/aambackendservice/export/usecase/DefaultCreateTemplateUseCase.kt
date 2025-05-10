package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.export.core.CreateTemplateData
import com.aamdigital.aambackendservice.export.core.CreateTemplateError.CREATE_TEMPLATE_REQUEST_FAILED_ERROR
import com.aamdigital.aambackendservice.export.core.CreateTemplateError.PARSE_RESPONSE_ERROR
import com.aamdigital.aambackendservice.export.core.CreateTemplateRequest
import com.aamdigital.aambackendservice.export.core.CreateTemplateUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.client.RestClient


data class CreateTemplateResponseDto(
    val success: Boolean,
    val data: CreateTemplateResponseDataDto
)

data class CreateTemplateResponseDataDto(
    val templateId: String,
)


/**
 * Default implementation of the [CreateTemplateUseCase] interface.
 *
 * This use case is responsible for registering a template file by making a POST request to a specified
 * template creation endpoint. The TemplateExport entity is then created in the frontend.
 *
 * @property restClient The RestClient used to make HTTP requests to the template engine.
 * @property objectMapper The ObjectMapper used to parse JSON responses.
 */
class DefaultCreateTemplateUseCase(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
) : CreateTemplateUseCase() {

    override fun apply(
        request: CreateTemplateRequest
    ): UseCaseOutcome<CreateTemplateData> {
        val builder = MultipartBodyBuilder()

        builder
            .part("template", request.file.resource)
            .filename(request.file.name)

        val response = try {
            restClient.post()
                .uri("/template")
                .accept(MediaType.APPLICATION_JSON)
                .body(builder.build())
                .retrieve()
                .body(String::class.java)
        } catch (it: Exception) {
            throw ExternalSystemException(
                cause = it,
                message = it.localizedMessage,
                code = CREATE_TEMPLATE_REQUEST_FAILED_ERROR
            )
        }

        if (response.isNullOrEmpty()) {
            return UseCaseOutcome.Failure(
                errorMessage = "Response from template service was null or empty.",
                errorCode = CREATE_TEMPLATE_REQUEST_FAILED_ERROR,
            )
        }

        return UseCaseOutcome.Success(
            data = CreateTemplateData(
                templateRef = DomainReference(parseResponse(response))
            )
        )
    }

    private fun parseResponse(raw: String): String {
        try {
            val renderApiClientResponse = objectMapper.readValue(raw, CreateTemplateResponseDto::class.java)
            return renderApiClientResponse.data.templateId
        } catch (ex: Exception) {

            throw ExternalSystemException(
                cause = ex,
                message = ex.localizedMessage,
                code = PARSE_RESPONSE_ERROR,
            )
        }
    }
}
