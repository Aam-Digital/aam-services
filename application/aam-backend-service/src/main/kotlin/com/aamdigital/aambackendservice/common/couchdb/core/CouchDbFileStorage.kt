package com.aamdigital.aambackendservice.common.couchdb.core

import com.aamdigital.aambackendservice.common.couchdb.core.DefaultCouchDbClient.DefaultCouchDbClientErrorCode
import com.aamdigital.aambackendservice.common.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.common.domain.FileStorage
import com.aamdigital.aambackendservice.common.domain.FileStorageError
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.common.stream.handleInputStreamToOutputStream
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import java.io.IOException
import java.io.InputStream


/**
 * Uses CouchDb attachments as file storage
 */
class CouchDbFileStorage(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
) : FileStorage {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * see: https://docs.couchdb.org/en/stable/api/document/attachments.html
     */
    override fun storeFile(
        path: String,
        fileName: String,
        file: InputStream
    ) {
        val fileHeaders = prefetchFile(path)

        val response = restClient.put()
            .uri("$path/$fileName")
            .body { outputStream ->
                handleInputStreamToOutputStream(outputStream, file)
            }
            .headers {
                fileHeaders.forEach { (key, value) ->
                    it[key] = value
                }
            }
            .retrieve()
            .body(String::class.java)

        val docSuccess = try {
            objectMapper.readValue(response, DocSuccess::class.java)
        } catch (ex: IOException) {
            throw ExternalSystemException(
                message = "Could not parse response to DocSuccess",
                cause = ex,
                code = FileStorageError.IO_ERROR
            )
        }

        if (!docSuccess.ok) {
            throw ExternalSystemException(
                message = "Could not store File. Response: $response",
                code = FileStorageError.IO_ERROR,
            )
        }
    }

    override fun fetchFile(path: String, fileName: String): InputStream {
        val response = restClient.get()
            .uri("$path/$fileName")
            .accept(MediaType.APPLICATION_OCTET_STREAM)
            .retrieve()
            .onStatus({ it.is4xxClientError }, { _, response ->
                when (response.statusCode) {
                    HttpStatus.NOT_FOUND -> throw NotFoundException(
                        message = "Could not find file: $path/$fileName",
                        code = DefaultCouchDbClientErrorCode.NOT_FOUND
                    )

                    else -> throw InvalidArgumentException(
                        message = "Server responded with ${response.statusCode} for $path/$fileName.",
                        code = DefaultCouchDbClientErrorCode.CLIENT_ERROR
                    )
                }
            })
            .body(Resource::class.java)


        if (response == null) {
            logger.debug("Response from CouchDb is null for path: $path/$fileName")
            throw ExternalSystemException(
                message = "Could not parse response to Resource",
                code = DefaultCouchDbClientErrorCode.EMPTY_RESPONSE
            )
        }

        if (!response.exists() || !response.isReadable) {
            logger.debug(
                "Response from CouchDb for {} not accessible; exists: {}, isReadable: {}",
                "$path/$fileName",
                response.exists(),
                response.isReadable
            )
            throw ExternalSystemException(
                message = "Resource is not readable or does not exist.",
                code = DefaultCouchDbClientErrorCode.EMPTY_RESPONSE
            )
        }

        return response.inputStream
    }

    private fun prefetchFile(
        path: String,
    ): HttpHeaders {
        val fileHeaders = restClient
            .head()
            .uri(path)
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

        val etag = fileHeaders.eTag?.replace("\"", "")

        return HttpHeaders().apply {
            if (etag.isNullOrBlank().not()) {
                set("If-Match", etag)
            }
        }
    }
}
