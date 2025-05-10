package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome.Success
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.NetworkException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.export.core.FetchTemplateData
import com.aamdigital.aambackendservice.export.core.FetchTemplateError
import com.aamdigital.aambackendservice.export.core.FetchTemplateRequest
import com.aamdigital.aambackendservice.export.core.FetchTemplateUseCase
import com.aamdigital.aambackendservice.export.core.TemplateExport
import com.aamdigital.aambackendservice.export.core.TemplateStorage
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Default implementation of the [FetchTemplateUseCase].
 *
 * This use case is responsible for fetching a template based on a given request by making a GET request
 * to a specified template fetch endpoint.
 *
 * @property restClient The RestClient used to make HTTP requests to the template engine.
 * @property templateStorage The TemplateStorage instance used to fetch template metadata.
 */
class DefaultFetchTemplateUseCase(
    private val restClient: RestClient,
    private val templateStorage: TemplateStorage,
) : FetchTemplateUseCase() {

    private data class FileResponse(
        val file: InputStream,
        val headers: HttpHeaders,
    )

    override fun apply(
        request: FetchTemplateRequest
    ): UseCaseOutcome<FetchTemplateData> {
        val template = fetchTemplateRequest(request.templateRef)

        var responseHeaders = HttpHeaders()

        val fileStream = try {
            restClient.get()
                .uri("/template/${template.templateId}")
                .accept(MediaType.APPLICATION_JSON)
                .exchange { _, clientResponse ->

                    if (clientResponse.statusCode.is4xxClientError) {
                        throw NotFoundException(
                            code = FetchTemplateError.NOT_FOUND_ERROR,
                            message = "Template not found in template engine. Please re-upload" +
                                    " the template and try again."
                        )
                    }

                    responseHeaders = clientResponse.headers

                    clientResponse.bodyTo(ByteArray::class.java)
                        ?: throw ExternalSystemException(
                            code = FetchTemplateError.FETCH_TEMPLATE_REQUEST_FAILED_ERROR,
                            message = "Could not fetch the template file from the template engine."
                        )
                }
        } catch (ex: ResourceAccessException) {
            throw NetworkException(
                cause = ex,
                message = ex.localizedMessage,
                code = FetchTemplateError.FETCH_TEMPLATE_REQUEST_FAILED_ERROR
            )
        }

        val forwardHeaders = HttpHeaders()
        forwardHeaders.contentType = responseHeaders.contentType

        if (!responseHeaders["Content-Disposition"].isNullOrEmpty()) {
            forwardHeaders["Content-Disposition"] = responseHeaders["Content-Disposition"]
        }

        val inputStream = ByteArrayInputStream(fileStream)

        val fileResponse = FileResponse(
            file = inputStream,
            headers = forwardHeaders
        )

        return Success(
            data = FetchTemplateData(
                file = fileResponse.file,
                responseHeaders = fileResponse.headers
            )
        )
    }

    private fun fetchTemplateRequest(templateRef: DomainReference): TemplateExport {
        return try {
            templateStorage.fetchTemplate(templateRef)
        } catch (ex: NotFoundException) {
            throw NotFoundException(
                cause = ex,
                message = ex.localizedMessage,
                code = FetchTemplateError.NOT_FOUND_ERROR
            )
        } catch (ex: Exception) {
            throw ExternalSystemException(
                cause = ex,
                message = ex.localizedMessage,
                code = FetchTemplateError.FETCH_TEMPLATE_REQUEST_FAILED_ERROR
            )
        }
    }
}
