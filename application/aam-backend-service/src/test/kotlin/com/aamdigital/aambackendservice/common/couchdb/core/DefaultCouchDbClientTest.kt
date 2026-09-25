package com.aamdigital.aambackendservice.common.couchdb.core

import com.aamdigital.aambackendservice.common.couchdb.core.DefaultCouchDbClient.Companion.FIND_PAGE_SIZE
import com.aamdigital.aambackendservice.common.couchdb.dto.FindResponse
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient

class DefaultCouchDbClientTest {
    private val objectMapper = ObjectMapper()
    private val couchDbClient = spy(DefaultCouchDbClient(mock<RestClient>(), objectMapper))

    private fun stubFind(vararg pages: FindResponse<Any>) {
        doReturn(pages.first(), *pages.drop(1).toTypedArray())
            .`when`(couchDbClient)
            .findDatabaseDocuments(any(), any(), any(), eq(Any::class))
    }

    @Test
    fun `finds documents by prefix within the id range of that prefix`() {
        stubFind(FindResponse(docs = listOf("a")))

        val docs =
            couchDbClient.findDatabaseDocumentsByPrefix(
                database = "db",
                prefix = "UserDevice",
                selector = mapOf("userIdentifier" to "user-1"),
                limit = 10,
                kClass = Any::class
            )

        assertThat(docs).containsExactly("a")
        verify(couchDbClient).findDatabaseDocuments(
            eq("db"),
            eq(
                mapOf(
                    "selector" to
                        mapOf(
                            "_id" to mapOf("\$gt" to "UserDevice:", "\$lt" to "UserDevice:\ufff0"),
                            "userIdentifier" to "user-1"
                        ),
                    "limit" to 10
                )
            ),
            any(),
            eq(Any::class)
        )
    }

    @Test
    fun `finds all matches page by page when no limit is given`() {
        val fullPage = List(FIND_PAGE_SIZE) { "doc-$it" }
        stubFind(
            FindResponse(docs = fullPage, bookmark = "b1"),
            FindResponse(docs = listOf("last"), bookmark = "b2")
        )

        val docs = couchDbClient.findDatabaseDocumentsByPrefix(database = "db", prefix = "P", kClass = Any::class)

        assertThat(docs).hasSize(FIND_PAGE_SIZE + 1).endsWith("last")
        verify(couchDbClient, times(2)).findDatabaseDocuments(any(), any(), any(), eq(Any::class))
        verify(couchDbClient).findDatabaseDocuments(
            eq("db"),
            argThat<Map<String, Any>> { this["bookmark"] == "b1" && this["limit"] == FIND_PAGE_SIZE },
            any(),
            eq(Any::class)
        )
    }

    private fun headClientRespondingWith(status: HttpStatus): DefaultCouchDbClient {
        val builder = RestClient.builder()
        MockRestServiceServer
            .bindTo(builder)
            .build()
            .expect(requestTo("/db/doc"))
            .andExpect(method(HttpMethod.HEAD))
            .andRespond(withStatus(status))
        return DefaultCouchDbClient(builder.build(), ObjectMapper())
    }

    @Test
    fun `answers a HEAD for a missing document with empty headers`() {
        val headers = headClientRespondingWith(HttpStatus.NOT_FOUND).headDatabaseDocument("db", "doc")

        assertThat(headers.eTag).isNull()
    }

    @Test
    fun `does not mistake a forbidden HEAD for a missing document`() {
        assertThatThrownBy { headClientRespondingWith(HttpStatus.FORBIDDEN).headDatabaseDocument("db", "doc") }
            .isInstanceOf(ExternalSystemException::class.java)
            .extracting { (it as ExternalSystemException).code }
            .isEqualTo(DefaultCouchDbClient.DefaultCouchDbClientErrorCode.CLIENT_ERROR)
    }

    @Test
    fun `gets all documents by prefix from all_docs`() {
        val response =
            objectMapper.readTree(
                """{"rows": [{"id": "P:1", "doc": {"name": "one"}}, {"id": "P:2", "doc": {"name": "two"}}]}"""
            ) as ObjectNode
        doReturn(response)
            .`when`(couchDbClient)
            .getDatabaseDocument(eq("db"), eq("_all_docs"), any(), eq(ObjectNode::class))

        val docs = couchDbClient.getDatabaseDocumentsByPrefix(database = "db", prefix = "P", kClass = Map::class)

        assertThat(docs).containsExactly(mapOf("name" to "one"), mapOf("name" to "two"))
        verify(couchDbClient).getDatabaseDocument(
            eq("db"),
            eq("_all_docs"),
            argThat<MultiValueMap<String, String>> {
                getFirst("startkey") == "\"P:\"" && getFirst("endkey") == "\"P:\\ufff0\"" &&
                    getFirst("include_docs") == "true"
            },
            eq(ObjectNode::class)
        )
    }

    private fun assertAllDocsResponseRejected(json: String) {
        doReturn(objectMapper.readTree(json) as ObjectNode)
            .`when`(couchDbClient)
            .getDatabaseDocument(eq("db"), eq("_all_docs"), any(), eq(ObjectNode::class))

        assertThatThrownBy {
            couchDbClient.getDatabaseDocumentsByPrefix(database = "db", prefix = "P", kClass = Map::class)
        }
            .isInstanceOf(ExternalSystemException::class.java)
            .extracting { (it as ExternalSystemException).code }
            .isEqualTo(DefaultCouchDbClient.DefaultCouchDbClientErrorCode.PARSING_ERROR)
    }

    @Test
    fun `rejects an all_docs response without a rows array`() {
        assertAllDocsResponseRejected("""{"total_rows": 0}""")
        assertAllDocsResponseRejected("""{"rows": {}}""")
    }

    @Test
    fun `rejects an all_docs row without its doc`() {
        assertAllDocsResponseRejected("""{"rows": [{"id": "P:1", "doc": {"name": "one"}}, {"id": "P:2"}]}""")
        assertAllDocsResponseRejected("""{"rows": [{"id": "P:1", "doc": null}]}""")
    }
}
