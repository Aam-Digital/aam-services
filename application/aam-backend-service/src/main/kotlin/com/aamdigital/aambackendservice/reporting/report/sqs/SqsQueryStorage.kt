package com.aamdigital.aambackendservice.reporting.report.sqs

import com.aamdigital.aambackendservice.error.InvalidArgumentException
import com.aamdigital.aambackendservice.reporting.report.core.QueryStorage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

data class QueryRequest(
    val query: String,
    val args: List<String>
)

data class QueryResult(
    val result: List<*>
)

@Service
class SqsQueryStorage(
    @Qualifier("sqs-client") private val sqsClient: WebClient,
    private val schemaService: SqsSchemaService,
) : QueryStorage {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun executeQuery(query: QueryRequest): Mono<QueryResult> {
        val schemaPath = schemaService.getSchemaPath()
        return schemaService.updateSchema()
            .flatMap {
                sqsClient.post()
                    .uri(schemaPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(query))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchangeToMono { response ->
                        if (response.statusCode().is2xxSuccessful) {
                            response.bodyToMono(List::class.java)
                                .map {
                                    QueryResult(result = it)
                                }
                        } else {
                            response.bodyToMono(String::class.java)
                                .flatMap {
                                    logger.error("[SqsQueryStorage] Invalid response from SQS: $it")
                                    Mono.error(InvalidArgumentException(it))
                                }
                        }
                    }
            }
            .onErrorResume {
                logger.error("[SqsQueryStorage]: ${it.localizedMessage}", it)
                Mono.error(InvalidArgumentException(it.localizedMessage))
            }
    }
}
