package com.aamdigital.aambackendservice.reporting.report.sqs

import com.aamdigital.aambackendservice.error.InvalidArgumentException
import com.aamdigital.aambackendservice.reporting.report.core.QueryStorage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyExtractors
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.kotlin.core.publisher.toFlux

data class QueryRequest(
    val query: String,
    val args: List<String>
)

@Service
class SqsQueryStorage(
    @Qualifier("sqs-client") private val sqsClient: WebClient,
    private val schemaService: SqsSchemaService,
) : QueryStorage {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun executeQuery(query: QueryRequest): Flux<DataBuffer> {
        val schemaPath = schemaService.getSchemaPath()
        return schemaService.updateSchema()
            .toFlux()
            .flatMap {
                sqsClient.post()
                    .uri(schemaPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(query))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchangeToFlux { response ->
                        if (response.statusCode().is2xxSuccessful) {
                            response.body(BodyExtractors.toDataBuffers())
                        } else {
                            response.bodyToFlux(String::class.java)
                                .flatMap {
                                    logger.error(
                                        "[SqsQueryStorage] " +
                                                "Invalid response (${response.statusCode()}) from SQS: $it"
                                    )
                                    Flux.error(InvalidArgumentException(it))
                                }
                        }
                    }
                    .onErrorResume {
                        logger.error("[SqsQueryStorage]: ${it.localizedMessage}", it)
                        Flux.error(InvalidArgumentException(it.localizedMessage))
                    }
            }

    }
}
