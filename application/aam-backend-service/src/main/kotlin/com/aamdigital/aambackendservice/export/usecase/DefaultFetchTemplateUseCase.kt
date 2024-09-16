package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.domain.UseCaseOutcome.Success
import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.error.ExternalSystemException
import com.aamdigital.aambackendservice.error.InvalidArgumentException
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.export.core.ExportTemplate
import com.aamdigital.aambackendservice.export.core.FetchTemplateData
import com.aamdigital.aambackendservice.export.core.FetchTemplateErrorCode
import com.aamdigital.aambackendservice.export.core.FetchTemplateRequest
import com.aamdigital.aambackendservice.export.core.FetchTemplateUseCase
import com.aamdigital.aambackendservice.export.core.TemplateStorage
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty

class DefaultFetchTemplateUseCase(
    private val webClient: WebClient,
    private val templateStorage: TemplateStorage,
) : FetchTemplateUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    private data class FileResponse(
        val file: DataBuffer,
        val headers: HttpHeaders,
    )

    override fun apply(
        request: FetchTemplateRequest
    ): Mono<UseCaseOutcome<FetchTemplateData, FetchTemplateErrorCode>> {
        return try {
            fetchTemplateRequest(request.templateRef)
                .flatMap { template ->
                    webClient.get()
                        .uri("/template/${template.templateId}")
                        .accept(MediaType.APPLICATION_JSON)
                        .exchangeToMono { exchange ->
                            exchange.bodyToMono(DataBuffer::class.java).map { dataBuffer ->
                                val responseHeaders = exchange.headers().asHttpHeaders()

                                val forwardHeaders = HttpHeaders()
                                forwardHeaders.contentType = responseHeaders.contentType

                                if (!responseHeaders["Content-Disposition"].isNullOrEmpty()) {
                                    forwardHeaders["Content-Disposition"] = responseHeaders["Content-Disposition"]
                                }

                                FileResponse(
                                    file = dataBuffer,
                                    headers = forwardHeaders
                                )
                            }
                        }
                        .onErrorMap {
                            ExternalSystemException(
                                cause = it,
                                message = it.localizedMessage,
                                code = FetchTemplateErrorCode.FETCH_TEMPLATE_REQUEST_FAILED_ERROR.toString()
                            )
                        }
                        .flatMap { fileResponse: FileResponse ->
                            Mono.just(
                                Success(
                                    outcome = FetchTemplateData(
                                        file = fileResponse.file,
                                        responseHeaders = fileResponse.headers
                                    )
                                )
                            )
                        }
                }
        } catch (it: Exception) {
            handleError(it)
        }
    }

    override fun handleError(it: Throwable): Mono<UseCaseOutcome<FetchTemplateData, FetchTemplateErrorCode>> {
        val errorCode: FetchTemplateErrorCode = runCatching {
            FetchTemplateErrorCode.valueOf((it as AamException).code)
        }.getOrDefault(FetchTemplateErrorCode.INTERNAL_SERVER_ERROR)

        logger.error("[${errorCode}] ${it.localizedMessage}", it.cause)

        return Mono.just(
            UseCaseOutcome.Failure(
                errorMessage = it.localizedMessage,
                errorCode = errorCode,
                cause = it.cause
            )
        )
    }

    private fun fetchTemplateRequest(templateRef: DomainReference): Mono<ExportTemplate> {
        return templateStorage.fetchTemplate(templateRef)
            .switchIfEmpty {
                Mono.error(
                    ExternalSystemException(
                        cause = null,
                        message = "fetchTemplate() returned empty Mono",
                        code = FetchTemplateErrorCode.FETCH_TEMPLATE_REQUEST_FAILED_ERROR.toString()
                    )
                )
            }
            .onErrorMap {
                if (it is InvalidArgumentException) {
                    NotFoundException(
                        cause = it,
                        message = it.localizedMessage,
                        code = FetchTemplateErrorCode.NOT_FOUND_ERROR.toString()
                    )
                } else {
                    ExternalSystemException(
                        cause = it,
                        message = it.localizedMessage,
                        code = FetchTemplateErrorCode.FETCH_TEMPLATE_REQUEST_FAILED_ERROR.toString()
                    )
                }
            }
    }
}
