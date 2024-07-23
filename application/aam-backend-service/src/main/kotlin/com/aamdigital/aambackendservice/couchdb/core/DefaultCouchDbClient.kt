package com.aamdigital.aambackendservice.couchdb.core

import com.aamdigital.aambackendservice.couchdb.dto.CouchDbChangesResponse
import com.aamdigital.aambackendservice.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.couchdb.dto.FindResponse
import com.aamdigital.aambackendservice.error.InternalServerException
import com.aamdigital.aambackendservice.error.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.reflect.KClass

class DefaultCouchDbClient(
    private val webClient: WebClient,
    private val objectMapper: ObjectMapper
) : CouchDbClient {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val CHANGES_URL = "/_changes"
        private const val FIND_URL = "/_find"
    }

    override fun allDatabases(): Mono<List<String>> {
        return webClient
            .get()
            .uri("/_all_dbs")
            .accept(MediaType.APPLICATION_JSON)
            .exchangeToMono { response ->
                response.bodyToMono(object : ParameterizedTypeReference<List<String>>() {})
            }
    }

    override fun changes(
        database: String, queryParams: MultiValueMap<String, String>
    ): Mono<CouchDbChangesResponse> {
        return webClient.get().uri {
            it.path("/$database/$CHANGES_URL")
            it.queryParams(queryParams)
            it.build()
        }.accept(MediaType.APPLICATION_JSON).exchangeToMono { response ->
            response.bodyToMono(CouchDbChangesResponse::class.java).mapNotNull {
                it
            }
        }
    }

    override fun <T : Any> find(
        database: String, body: Map<String, Any>, queryParams: MultiValueMap<String, String>, kClass: KClass<T>
    ): Mono<FindResponse<T>> {
        return webClient.post().uri {
            it.path("/$database/$FIND_URL")
            it.queryParams(queryParams)
            it.build()
        }.contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(body)).exchangeToMono {
            it.bodyToMono(ObjectNode::class.java).map { objectNode ->
                val data =
                    (objectMapper.convertValue(objectNode, Map::class.java)["docs"] as Iterable<*>).map { entry ->
                        objectMapper.convertValue(entry, kClass.java)
                    }

                FindResponse(docs = data)
            }
        }
    }

    override fun headDatabaseDocument(
        database: String,
        documentId: String,
    ): Mono<HttpHeaders> {
        return webClient
            .head()
            .uri {
                it.path("/$database/$documentId")
                it.build()
            }
            .accept(MediaType.APPLICATION_JSON).exchangeToMono {
                if (it.statusCode().is2xxSuccessful) {
                    Mono.just(it.headers().asHttpHeaders())
                } else if (it.statusCode().is4xxClientError) {
                    Mono.just(HttpHeaders())
                } else {
                    throw InternalServerException()
                }
            }
    }

    override fun <T : Any> getDatabaseDocument(
        database: String,
        documentId: String,
        queryParams: MultiValueMap<String, String>,
        kClass: KClass<T>,
    ): Mono<T> {
        return webClient.get().uri {
            it.path("/$database/$documentId")
            it.queryParams(queryParams)
            it.build()
        }.accept(MediaType.APPLICATION_JSON).exchangeToMono {
            handleResponse(it, kClass)
        }
    }

    override fun putDatabaseDocument(
        database: String,
        documentId: String,
        body: Any,
    ): Mono<DocSuccess> {
        return headDatabaseDocument(
            database = database,
            documentId = documentId
        ).flatMap { httpHeaders ->
            val etag = httpHeaders.eTag?.replace("\"", "")

            webClient.put()
                .uri {
                    it.path("/$database/$documentId")
                    it.build()
                }
                .body(BodyInserters.fromValue(body))
                .headers {
                    if (etag.isNullOrBlank().not()) {
                        it.set("If-Match", etag)
                    }
                }
                .accept(MediaType.APPLICATION_JSON).exchangeToMono {
                    handleResponse(it, DocSuccess::class)
                }
        }
    }

    override fun <T : Any> getPreviousDocRev(
        database: String,
        documentId: String,
        rev: String,
        kClass: KClass<T>,
    ): Mono<T> {
        val allRevsInfoQueryParams = getEmptyQueryParams()
        allRevsInfoQueryParams.set("revs_info", "true")

        return getDatabaseDocument(
            database = database,
            documentId = documentId,
            queryParams = allRevsInfoQueryParams,
            kClass = ObjectNode::class
        ).flatMap { currentDoc ->
            val revInfo = currentDoc.get("_revs_info") ?: return@flatMap Mono.empty()

            if (!revInfo.isArray) {
                return@flatMap Mono.empty()
            }

            val revIndex = revInfo.indexOfFirst { jsonNode -> jsonNode.get("rev").textValue().equals(rev) }

            if (revIndex == -1) {
                return@flatMap Mono.empty()
            }

            if (revIndex + 1 >= revInfo.size()) {
                return@flatMap Mono.empty()
            }

            val previousRef = revInfo.get(revIndex + 1).get("rev").textValue()

            val previousRevQueryParams = getEmptyQueryParams()
            previousRevQueryParams.set("rev", previousRef)

            getDatabaseDocument(
                database = database,
                documentId = documentId,
                queryParams = previousRevQueryParams,
                kClass = ObjectNode::class
            ).map { previousDoc ->
                objectMapper.convertValue(previousDoc, kClass.java)
            }
        }
    }

    override fun getAttachment(
        database: String,
        documentId: String,
        attachmentId: String,
    ): Flux<DataBuffer> {
        return webClient.get()
            .uri {
                it.path("$database/$documentId/$attachmentId")
                it.build()
            }
            .accept(MediaType.APPLICATION_OCTET_STREAM)
            .retrieve()
            .onStatus({ it.is4xxClientError }, {
                Mono.error(NotFoundException("Could not find attachment: $database/$documentId/$attachmentId"))
            })
            .bodyToFlux(DataBuffer::class.java)
            .doOnError {
                logger.warn(it.localizedMessage, it)
            }
    }

    override fun headAttachment(
        database: String,
        documentId: String,
        attachmentId: String,
    ): Mono<HttpHeaders> {
        return webClient.head()
            .uri {
                it.path("$database/$documentId/$attachmentId")
                it.build()
            }
            .accept(MediaType.APPLICATION_JSON).exchangeToMono {
                if (it.statusCode().is2xxSuccessful) {
                    Mono.just(it.headers().asHttpHeaders())
                } else if (it.statusCode().is4xxClientError) {
                    Mono.just(HttpHeaders())
                } else {
                    throw InternalServerException()
                }
            }
    }

    override fun putAttachment(
        database: String,
        documentId: String,
        attachmentId: String,
        file: Flux<DataBuffer>
    ): Mono<DocSuccess> {
        return headDatabaseDocument(
            database = database,
            documentId = documentId,
        )
            .flatMap { httpHeaders ->
                val etag = httpHeaders.eTag?.replace("\"", "")
                webClient.put()
                    .uri {
                        it.path("$database/$documentId/$attachmentId")
                        it.build()
                    }
                    .body(BodyInserters.fromDataBuffers(file))
                    .headers {
                        if (etag.isNullOrBlank().not()) {
                            it.set("If-Match", etag)
                        }
                        it.contentType = MediaType.APPLICATION_JSON
                    }
                    .retrieve()
                    .bodyToMono(DocSuccess::class.java)
                    .doOnSuccess {
                        logger.trace("[CouchDbClient] PUT Attachment response: {}", it)
                    }
                    .doOnError {
                        logger.error("[CouchDbClient] PUT Attachment failed: {}", it.localizedMessage)
                    }
            }
    }

    private fun <T : Any> handleResponse(
        response: ClientResponse, typeReference: KClass<T>
    ): Mono<T> {
        return response.bodyToMono(typeReference.java).mapNotNull {
            it
        }
    }
}
