package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.domain.UseCaseOutcome.Success
import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.error.ExternalSystemException
import com.aamdigital.aambackendservice.export.core.FetchTemplateData
import com.aamdigital.aambackendservice.export.core.FetchTemplateErrorCode
import com.aamdigital.aambackendservice.export.core.FetchTemplateRequest
import com.aamdigital.aambackendservice.export.core.FetchTemplateUseCase
import com.aamdigital.aambackendservice.export.core.RenderTemplateErrorCode
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class DefaultFetchTemplateUseCase(
    private val webClient: WebClient,
) : FetchTemplateUseCase {

    private data class FileResponse(
        val file: DataBuffer,
        val headers: HttpHeaders,
    )

    override fun apply(
        request: FetchTemplateRequest
    ): Mono<UseCaseOutcome<FetchTemplateData, FetchTemplateErrorCode>> {
        return try {
            webClient.get()
                .uri("/template/${request.templateRef.id}")
                .accept(MediaType.APPLICATION_JSON)
                .exchangeToMono { exchange ->
                    exchange.bodyToMono(DataBuffer::class.java).map { dataBuffer ->
                        val responseHeaders = exchange.headers().asHttpHeaders()

                        val forwardHeaders = HttpHeaders()
                        forwardHeaders.contentType = responseHeaders.contentType
                        forwardHeaders["Content-Disposition"] = responseHeaders["Content-Disposition"]

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
                        code = RenderTemplateErrorCode.CREATE_RENDER_REQUEST_FAILED_ERROR.toString()
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
        } catch (it: Exception) {
            handleError(it)
        }
    }

    override fun handleError(it: Throwable): Mono<UseCaseOutcome<FetchTemplateData, FetchTemplateErrorCode>> {
        val errorCode: FetchTemplateErrorCode = runCatching {
            FetchTemplateErrorCode.valueOf((it as AamException).code)
        }.getOrDefault(FetchTemplateErrorCode.INTERNAL_SERVER_ERROR)

        return Mono.just(
            UseCaseOutcome.Failure(
                errorMessage = it.message,
                errorCode = errorCode,
                cause = it.cause
            )
        )
    }
}
