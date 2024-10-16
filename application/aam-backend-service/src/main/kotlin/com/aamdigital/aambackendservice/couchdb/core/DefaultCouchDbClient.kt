package com.aamdigital.aambackendservice.couchdb.core

import com.aamdigital.aambackendservice.couchdb.dto.CouchDbChangesResponse
import com.aamdigital.aambackendservice.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.couchdb.dto.FindResponse
import com.aamdigital.aambackendservice.error.AamErrorCode
import com.aamdigital.aambackendservice.error.ExternalSystemException
import com.aamdigital.aambackendservice.error.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import java.io.InputStream
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
    }

    companion object {
        private const val CHANGES_URL = "/_changes"
        private const val FIND_URL = "/_find"
        private const val BYTE_ARRAY_BUFFER_LENGTH = 4096
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

    override fun getAttachment(
        database: String,
        documentId: String,
        attachmentId: String,
    ): InputStream {
        val response = httpClient.get()
            .uri {
                it.path("$database/$documentId/$attachmentId")
                it.build()
            }
            .accept(MediaType.APPLICATION_OCTET_STREAM)
            .retrieve()
            .onStatus({ it.is4xxClientError }, { _, _ ->
                throw NotFoundException(
                    message = "Could not find attachment: $database/$documentId/$attachmentId",
                    code = DefaultCouchDbClientErrorCode.NOT_FOUND
                )
            })
            .onStatus({ it.is5xxServerError }, { _, response ->
                logger.warn(
                    "[DefaultCouchDbClient.getAttachment] CouchDb responses with ${response.statusCode.value()} error"
                )
            })
            .body(Resource::class.java)


        if (response == null) {
            throw ExternalSystemException(
                message = "Could not parse response to Resource",
                code = DefaultCouchDbClientErrorCode.EMPTY_RESPONSE
            )
        }

        return response.inputStream
    }

    override fun headAttachment(
        database: String,
        documentId: String,
        attachmentId: String,
    ): HttpHeaders {
        return httpClient.head()
            .uri {
                it.path("$database/$documentId/$attachmentId")
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

    override fun putAttachment(
        database: String,
        documentId: String,
        attachmentId: String,
        file: InputStream
    ): DocSuccess {
        val httpHeaders = headDatabaseDocument(
            database = database,
            documentId = documentId,
        )
        val etag = httpHeaders.eTag?.replace("\"", "")

        val response = httpClient.put()
            .uri {
                it.path("$database/$documentId/$attachmentId")
                it.build()
            }
            .headers {
                if (etag.isNullOrBlank().not()) {
                    it.set("If-Match", etag)
                }
                it.contentType = MediaType.APPLICATION_JSON
            }
            .body { outputStream ->
                val buffer = ByteArray(BYTE_ARRAY_BUFFER_LENGTH)
                var bytesRead: Int
                while ((file.read(buffer).also { bytesRead = it }) != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
            .retrieve()
            .body(DocSuccess::class.java)

        if (response == null) {
            throw ExternalSystemException(
                message = "[CouchDbClient] PUT Attachment failed",
                code = DefaultCouchDbClientErrorCode.EMPTY_RESPONSE
            )
        }

        logger.trace("[CouchDbClient] PUT Attachment response: {}", response.id)

        return response
    }

    @Throws(ExternalSystemException::class)
    private fun <T : Any> handleResponse(
        response: RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse,
        typeReference: KClass<T>
    ): T {
        val rawResponse = try {
            response.bodyTo(String::class.java)
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
}
