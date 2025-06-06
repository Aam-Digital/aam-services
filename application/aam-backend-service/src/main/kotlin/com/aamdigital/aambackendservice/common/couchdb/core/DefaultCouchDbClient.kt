package com.aamdigital.aambackendservice.common.couchdb.core

import com.aamdigital.aambackendservice.common.couchdb.dto.CouchDbChangesResponse
import com.aamdigital.aambackendservice.common.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.common.couchdb.dto.FindResponse
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import java.io.InterruptedIOException
import java.util.*
import kotlin.reflect.KClass

class DefaultCouchDbClient(
    private val httpClient: RestClient,
    private val objectMapper: ObjectMapper
) : CouchDbClient {

    private val logger = LoggerFactory.getLogger(javaClass)

    enum class DefaultCouchDbClientErrorCode : AamErrorCode {
        INVALID_RESPONSE,
        PARSING_ERROR,
        EMPTY_RESPONSE,
        NOT_FOUND,
        CLIENT_ERROR,
        OTHER_COUCHDB_ERROR
    }

    companion object {
        private const val CHANGES_URL = "/_changes"
        private const val FIND_URL = "/_find"
    }

    override fun allDatabases(): List<String> {
        val response = httpClient
            .get()
            .uri("/_all_dbs")
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(object : ParameterizedTypeReference<List<String>>() {})

        if (response.isNullOrEmpty()) {
            throw ExternalSystemException(
                message = "Could not parse response to List<String>",
                code = DefaultCouchDbClientErrorCode.EMPTY_RESPONSE
            )
        }

        return response
    }

    override fun changes(
        database: String, queryParams: MultiValueMap<String, String>
    ): CouchDbChangesResponse {
        val response = httpClient
            .get()
            .uri {
                it.path("/$database/$CHANGES_URL")
                it.queryParams(queryParams)
                it.build()
            }
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(CouchDbChangesResponse::class.java)

        if (response == null) {
            throw ExternalSystemException(
                message = "Could not parse response to CouchDbChangesResponse",
                code = DefaultCouchDbClientErrorCode.EMPTY_RESPONSE,
            )
        }

        return response
    }

    override fun <T : Any> find(
        database: String, body: Map<String, Any>, queryParams: MultiValueMap<String, String>, kClass: KClass<T>
    ): FindResponse<T> {
        val response = httpClient
            .post()
            .uri {
                it.path("/$database/$FIND_URL")
                it.queryParams(queryParams)
                it.build()
            }
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(ObjectNode::class.java)

        if (response == null) {
            throw ExternalSystemException(
                message = "Could not parse response to ObjectNode",
                code = DefaultCouchDbClientErrorCode.EMPTY_RESPONSE
            )
        }

        // todo refactor to string parsing
        val data =
            (objectMapper.convertValue(response, Map::class.java)["docs"] as Iterable<*>).map { entry ->
                objectMapper.convertValue(entry, kClass.java)
            }

        return FindResponse(docs = data)
    }

    override fun headDatabaseDocument(
        database: String,
        documentId: String,
    ): HttpHeaders {
        return httpClient
            .head()
            .uri {
                it.path("/$database/$documentId")
                it.build()
            }
            .accept(MediaType.APPLICATION_JSON)
            .exchange { _, clientResponse ->
                if (clientResponse.statusCode.is2xxSuccessful) {
                    clientResponse.headers
                } else if (clientResponse.statusCode.is4xxClientError) {
                    HttpHeaders()
                } else {
                    throw ExternalSystemException(
                        message = "Retrieved HTTP 500 from CouchDb, ${clientResponse.bodyTo(String::class.java)}",
                        code = DefaultCouchDbClientErrorCode.INVALID_RESPONSE
                    )
                }
            }
    }

    /**
     *  Fetch a document from the couchdb and return a parsed instance of given kClass.
     *
     *  @param database couchdb target database
     *  @param documentId couchdb document _id
     *  @param queryParams List of query params forwarded to couchdb request
     *  @param kClass response will be parsed with objectMapper to this class reference
     *
     * @throws NotFoundException Document is not available in Database
     * @throws ExternalSystemException Response could not be processed or parsed to requested kClass
     * @throws InterruptedIOException NetworkTimeout, SocketTimeout or similar
     */
    @Throws(
        NotFoundException::class,
        ExternalSystemException::class,
        InterruptedIOException::class
    )
    override fun <T : Any> getDatabaseDocument(
        database: String,
        documentId: String,
        queryParams: MultiValueMap<String, String>,
        kClass: KClass<T>,
    ): T {
        return httpClient.get()
            .uri {
                it.path("/$database/$documentId")
                it.queryParams(queryParams)
                it.build()
            }
            .accept(MediaType.APPLICATION_JSON)
            .exchange { _, clientResponse ->
                if (clientResponse.statusCode.is4xxClientError) {
                    throw NotFoundException(
                        message = "Document \"$documentId\" could not be found in database \"$database\"",
                        code = DefaultCouchDbClientErrorCode.NOT_FOUND
                    )
                }
                handleResponse(clientResponse, kClass)
            }
    }

    override fun putDatabaseDocument(
        database: String,
        documentId: String,
        body: Any,
    ): DocSuccess {
        val documentHeaders = headDatabaseDocument(
            database = database,
            documentId = documentId
        )

        val etag = documentHeaders.eTag?.replace("\"", "")

        return httpClient.put()
            .uri {
                it.path("/$database/$documentId")
                it.build()
            }
            .body(body)
            .headers {
                if (etag.isNullOrBlank().not()) {
                    it.set("If-Match", etag)
                }
            }
            .accept(MediaType.APPLICATION_JSON)
            .exchange { _, clientResponse ->
                handleResponse(clientResponse, DocSuccess::class)
            }
    }

    override fun deleteDatabaseDocument(
        database: String,
        documentId: String,
    ): DocSuccess {
        val documentHeaders = headDatabaseDocument(
            database = database,
            documentId = documentId
        )

        val etag = documentHeaders.eTag?.replace("\"", "")

        return httpClient.delete()
            .uri {
                it.path("/$database/$documentId")
                it.build()
            }
            .headers {
                if (etag.isNullOrBlank().not()) {
                    it.set("If-Match", etag)
                }
            }
            .exchange { _, clientResponse ->
                handleResponse(clientResponse, DocSuccess::class)
            }
    }

    override fun <T : Any> getPreviousDocRev(
        database: String,
        documentId: String,
        rev: String,
        kClass: KClass<T>,
    ): Optional<T> {
        val allRevsInfoQueryParams = getEmptyQueryParams()
        allRevsInfoQueryParams.set("revs_info", "true")

        val currentDoc = try {
            getDatabaseDocument(
                database = database,
                documentId = documentId,
                queryParams = allRevsInfoQueryParams,
                kClass = ObjectNode::class
            )
        } catch (ex: NotFoundException) {
            return Optional.empty()
        }

        val revInfo = currentDoc.get("_revs_info") ?: return Optional.empty()

        if (!revInfo.isArray) {
            return Optional.empty()
        }

        val revIndex = revInfo.indexOfFirst { jsonNode -> jsonNode.get("rev").textValue().equals(rev) }

        if (revIndex == -1) {
            return Optional.empty()
        }

        if (revIndex + 1 >= revInfo.size()) {
            return Optional.empty()
        }

        val previousRef = revInfo.get(revIndex + 1).get("rev").textValue()

        val previousRevQueryParams = getEmptyQueryParams()
        previousRevQueryParams.set("rev", previousRef)

        val previousDoc = getDatabaseDocument(
            database = database,
            documentId = documentId,
            queryParams = previousRevQueryParams,
            kClass = ObjectNode::class
        )

        return Optional.of(objectMapper.convertValue(previousDoc, kClass.java))
    }

    @Throws(ExternalSystemException::class)
    private fun <T : Any> handleResponse(
        response: RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse,
        typeReference: KClass<T>
    ): T {
        val rawResponse = try {
            response.bodyTo(String::class.java) ?: throw ExternalSystemException(
                message = "[DefaultCouchDbClient] empty response from server",
                code = DefaultCouchDbClientErrorCode.EMPTY_RESPONSE
            )
        } catch (ex: Exception) {
            logger.error(
                "[DefaultCouchDbClient] Invalid response from couchdb. Could not parse response to String.",
                ex
            )
            throw ExternalSystemException(
                message = ex.localizedMessage,
                cause = ex,
                code = DefaultCouchDbClientErrorCode.INVALID_RESPONSE
            )
        }

        if (typeReference == String::class) {
            @Suppress("UNCHECKED_CAST")
            return rawResponse as T
        }

        try {
            val renderApiClientResponse = objectMapper.readValue(rawResponse, typeReference.java)
            return renderApiClientResponse
        } catch (ex: Exception) {
            logger.error("[DefaultCouchDbClient] Could not parse response to ${typeReference.java.canonicalName}", ex)
            throw ExternalSystemException(
                message = ex.localizedMessage,
                cause = ex,
                code = DefaultCouchDbClientErrorCode.PARSING_ERROR
            )
        }
    }

    override fun createDatabase(databaseName: String) {
        return httpClient.put()
            .uri {
                it.path("/$databaseName")
                it.build()
            }
            .body("")
            .accept(MediaType.APPLICATION_JSON)
            .exchange { _, clientResponse ->
                if (!clientResponse.statusCode.is2xxSuccessful) {
                    logger.error(
                        "Failed to create CouchDB $databaseName with status code ${clientResponse.statusCode} (${
                            clientResponse.bodyTo(
                                String::class.java
                            )
                        })"
                    )

                    throw ExternalSystemException(
                        message = "Could not create database $databaseName",
                        code = DefaultCouchDbClientErrorCode.OTHER_COUCHDB_ERROR
                    )
                }
            }
    }

    override fun databaseExists(name: String): Boolean {
        return httpClient.get()
            .uri {
                it.path("/$name")
                it.build()
            }
            .exchange { _, clientResponse ->
                return@exchange clientResponse.statusCode.is2xxSuccessful
            }
    }
}
