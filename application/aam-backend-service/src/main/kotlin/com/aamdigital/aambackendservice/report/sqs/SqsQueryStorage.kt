package com.aamdigital.aambackendservice.report.sqs

import com.aamdigital.aambackendservice.report.core.QueryStorage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

data class QueryRequest(
    val query: String
)

data class QueryResult(
    val result: List<*>
)

@Service
class SqsQueryStorage(
    @Qualifier("sqs-client") private val sqsClient: WebClient,
    private val schemaService: SqsSchemaService,
) : QueryStorage {
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
                        response.bodyToMono(List::class.java)
                            .map {
                                QueryResult(result = it)
                            }
                    }
            }
            .doOnError {
                println(it)
            }
    }
}
