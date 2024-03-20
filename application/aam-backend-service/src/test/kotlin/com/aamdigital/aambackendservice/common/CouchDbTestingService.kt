package com.aamdigital.aambackendservice.common

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate

class CouchDbTestingService(
    private val restTemplate: RestTemplate,
) {
    companion object {
        private val DEFAULT_DATABASES = listOf("app", "notification-webhook", "report-calculation")
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    fun reset() {
        val allDbsResponse = restTemplate
            .exchange("/_all_dbs", HttpMethod.GET, HttpEntity.EMPTY, ArrayNode::class.java)

        val dbs = allDbsResponse
            .body
            ?.filter {
                !it.textValue().startsWith("_")
            }
            ?.map {
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
        val response = restTemplate
            .exchange("/$database", HttpMethod.PUT, HttpEntity.EMPTY, ObjectNode::class.java)
        logger.info("[CouchDbSetup] create Database: $database, ${response.statusCode}")
    }

    private fun createDocument(database: String, documentName: String, document: Any) {
        val response = restTemplate
            .exchange(
                "/$database/$documentName",
                HttpMethod.PUT,
                HttpEntity(document),
                ObjectNode::class.java,
            )
        logger.info("[CouchDbSetup] create Document: $database, $documentName, ${response.statusCode}")
    }

    private fun deleteDatabase(database: String) {
        val response = restTemplate
            .exchange(
                "/$database",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                ObjectNode::class.java,
            )
        logger.info("[CouchDbSetup] delete Database: $database, ${response.statusCode}")
    }

}
