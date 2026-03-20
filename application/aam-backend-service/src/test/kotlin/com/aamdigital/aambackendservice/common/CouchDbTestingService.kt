package com.aamdigital.aambackendservice.common

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

class CouchDbTestingService(
    private val restTemplate: RestTemplate
) {
    companion object {
        private val DEFAULT_DATABASES = listOf("app", "notification-webhook", "report-calculation")
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    fun reset() {
        val allDbsResponse =
            restTemplate
                .exchange("/_all_dbs", HttpMethod.GET, HttpEntity.EMPTY, ArrayNode::class.java)

        val dbs =
            allDbsResponse
                .body
                ?.filter {
                    !it.textValue().startsWith("_")
                }?.map {
                    it.textValue()
                } ?: emptyList()

        dbs.forEach {
            deleteDatabase(it)
        }
    }

    fun initDefaultDatabases() {
        DEFAULT_DATABASES.forEach {
            createDatabase(it)
        }
    }

    fun createDatabase(database: String) {
        try {
            val response =
                restTemplate
                    .exchange("/$database", HttpMethod.PUT, HttpEntity.EMPTY, ObjectNode::class.java)
            logger.info("[CouchDbSetup] create Database: $database, ${response.statusCode}")
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 412) {
                logger.info("[CouchDbSetup] Database $database already exists, skipping creation.")
            } else {
                throw e
            }
        }
    }

    fun createDocument(
        database: String,
        documentName: String,
        documentContent: String
    ) {
        val response =
            restTemplate
                .exchange(
                    "/$database/${documentName.replaceFirst("_", ":")}",
                    HttpMethod.PUT,
                    HttpEntity(documentContent),
                    ObjectNode::class.java
                )
        logger.info("[CouchDbSetup] create Document: $database, $documentName, ${response.statusCode}")
    }

    fun updateDocument(
        database: String,
        documentName: String,
        documentContent: String
    ) {
        val docId = documentName.replaceFirst("_", ":")
        val responseHeaders = restTemplate.headForHeaders("/$database/$docId")
        val etag = responseHeaders.eTag?.replace("\"", "")

        val headers = LinkedMultiValueMap<String, String>()
        headers.set("If-Match", etag)

        val response =
            restTemplate
                .exchange(
                    "/$database/$docId",
                    HttpMethod.PUT,
                    HttpEntity(documentContent, headers),
                    ObjectNode::class.java
                )
        logger.info("[CouchDbSetup] update Document: $database, $documentName, ${response.statusCode}")
    }

    fun countDocuments(database: String): Int {
        return try {
            val response =
                restTemplate
                    .exchange("/$database/_all_docs", HttpMethod.GET, HttpEntity.EMPTY, ObjectNode::class.java)
            response.body?.get("total_rows")?.intValue() ?: 0
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 404) 0 else throw e
        }
    }

    fun getDocumentRev(database: String, docId: String): String? {
        return try {
            val headers = restTemplate.headForHeaders("/$database/$docId")
            headers.eTag?.replace("\"", "")
        } catch (e: HttpClientErrorException) {
            "ERROR: ${e.statusCode}"
        }
    }

    fun addAttachment(
        database: String,
        documentName: String,
        attachmentName: String,
        documentContent: String
    ) {
        val responseDocument =
            restTemplate
                .headForHeaders(
                    "/$database/${
                        documentName.replaceFirst(
                            "_",
                            ":"
                        )
                    }"
                )

        val etag = responseDocument.eTag?.replace("\"", "")

        val headers = LinkedMultiValueMap<String, String>()
        headers.set("If-Match", etag)

        val response =
            restTemplate
                .exchange(
                    "/$database/${
                        documentName.replaceFirst(
                            "_",
                            ":"
                        )
                    }/$attachmentName",
                    HttpMethod.PUT,
                    HttpEntity(documentContent, headers),
                    ObjectNode::class.java
                )
        logger.info(
            "[CouchDbSetup] create attachment:" +
                " $database, $documentName, $attachmentName, ${response.statusCode}"
        )
    }

    private fun deleteDatabase(database: String) {
        val response =
            restTemplate
                .exchange(
                    "/$database",
                    HttpMethod.DELETE,
                    HttpEntity.EMPTY,
                    ObjectNode::class.java
                )
        logger.info("[CouchDbSetup] delete Database: $database, ${response.statusCode}")
    }
}
