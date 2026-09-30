package com.aamdigital.aambackendservice.common.couchdb.core

import com.aamdigital.aambackendservice.common.couchdb.core.DefaultCouchDbClient.Companion.FIND_PAGE_SIZE
import com.aamdigital.aambackendservice.common.couchdb.core.DefaultCouchDbClient.DefaultCouchDbClientErrorCode
import com.aamdigital.aambackendservice.common.couchdb.dto.FindResponse
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
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
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseCreator
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.util.MultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient

class DefaultCouchDbClientTest {
    companion object {
        /** CouchDB's answer to a request for a document in a database that does not exist. */
        private const val MISSING_DATABASE_BODY = """{"error":"not_found","reason":"Database does not exist."}"""
        private const val DELETED_DOCUMENT_BODY = """{"error":"not_found","reason":"deleted"}"""
        private const val DATABASE_EXISTS_BODY =
            """{"error":"file_exists","reason":"The database could not be created, the file already exists."}"""
        private const val UNAUTHORIZED_BODY = """{"error":"unauthorized","reason":"You are not a server admin."}"""
    }

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

    /** A client whose requests are answered by [server], in the order they are expected there. */
    private fun clientAnswering(server: MockRestServiceServer.() -> Unit): DefaultCouchDbClient {
        val builder = RestClient.builder()
        MockRestServiceServer.bindTo(builder).build().server()
        return DefaultCouchDbClient(builder.build(), objectMapper)
    }

    private fun MockRestServiceServer.answer(
        httpMethod: HttpMethod,
        response: ResponseCreator,
        path: String = "/db/doc"
    ) {
        expect(requestTo(path))
            .andExpect(method(httpMethod))
            .andRespond(response)
    }

    private fun couchDbError(
        status: HttpStatus,
        body: String
    ): ResponseCreator = withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body)

    private fun headClientRespondingWith(status: HttpStatus): DefaultCouchDbClient =
        clientAnswering { answer(HttpMethod.HEAD, withStatus(status)) }

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
    fun `reports a write into a missing database as DATABASE_NOT_FOUND`() {
        // Given a HEAD answer has no body, so only the write learns that the database itself is missing
        val client =
            clientAnswering {
                answer(HttpMethod.HEAD, withStatus(HttpStatus.NOT_FOUND))
                answer(HttpMethod.PUT, couchDbError(HttpStatus.NOT_FOUND, MISSING_DATABASE_BODY))
            }

        // When
        val thrown = catchThrowable { client.putDatabaseDocument("db", "doc", mapOf("a" to 1)) }

        // Then
        assertThat(thrown)
            .isInstanceOf(ExternalSystemException::class.java)
            .extracting { (it as ExternalSystemException).code }
            .isEqualTo(DefaultCouchDbClientErrorCode.DATABASE_NOT_FOUND)
    }

    @Test
    fun `keeps reporting a missing document as NOT_FOUND`() {
        // Given
        val client =
            clientAnswering {
                answer(HttpMethod.HEAD, withStatus(HttpStatus.NOT_FOUND))
                answer(HttpMethod.DELETE, couchDbError(HttpStatus.NOT_FOUND, DELETED_DOCUMENT_BODY))
            }

        // When
        val thrown = catchThrowable { client.deleteDatabaseDocument("db", "doc") }

        // Then
        assertThat(thrown)
            .isInstanceOf(ExternalSystemException::class.java)
            .extracting { (it as ExternalSystemException).code }
            .isEqualTo(DefaultCouchDbClientErrorCode.NOT_FOUND)
    }

    @Test
    fun `does not take a 404 without CouchDB's error body for a missing database`() {
        // Given something in front of CouchDB answered, a proxy say
        val client =
            clientAnswering {
                answer(HttpMethod.PUT, withStatus(HttpStatus.NOT_FOUND).body("<html>Not Found</html>"))
            }

        // When
        val thrown = catchThrowable { client.putDatabaseDocumentAtRevision("db", "doc", mapOf("a" to 1), null) }

        // Then
        assertThat(thrown)
            .isInstanceOf(ExternalSystemException::class.java)
            .extracting { (it as ExternalSystemException).code }
            .isEqualTo(DefaultCouchDbClientErrorCode.NOT_FOUND)
    }

    @Test
    fun `leaves a query of a missing database to Spring's NotFound`() {
        // Given only writes are told apart: the outbox reads a missing database as an empty one by
        // catching exactly this
        val client =
            clientAnswering {
                answer(HttpMethod.POST, couchDbError(HttpStatus.NOT_FOUND, MISSING_DATABASE_BODY), path = "/db/_find")
            }

        // When
        val thrown = catchThrowable { client.findDatabaseDocuments("db", body = emptyMap(), kClass = Any::class) }

        // Then
        assertThat(thrown).isInstanceOf(HttpClientErrorException.NotFound::class.java)
    }

    @Test
    fun `passes on a find bookmark of another JSON type as text`() {
        // Given
        val client =
            clientAnswering {
                listOf("""{"docs":[],"bookmark":null}""", """{"docs":[],"bookmark":5}""", """{"docs":[]}""").forEach {
                    answer(
                        HttpMethod.POST,
                        withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(it),
                        path = "/db/_find"
                    )
                }
            }

        // When
        val bookmarks = List(3) { client.findDatabaseDocuments("db", body = emptyMap(), kClass = Any::class).bookmark }

        // Then
        assertThat(bookmarks).containsExactly("null", "5", null)
    }

    @Test
    fun `fails on revision infos that are not text when looking for the previous revision`() {
        // Given
        val client =
            clientAnswering {
                answer(
                    HttpMethod.GET,
                    withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            """{"_id":"doc","_rev":"2-b","_revs_info":[{"rev":2,"status":"available"},{"rev":"1-a"}]}"""
                        ),
                    path = "/db/doc?revs_info=true"
                )
            }

        // When
        val thrown =
            catchThrowable {
                client.getPreviousDocumentRevision("db", "doc", rev = "2-b", kClass = Map::class)
            }

        // Then
        assertThat(thrown).isInstanceOf(NullPointerException::class.java)
    }

    @Test
    fun `treats creating a database that already exists as done`() {
        // Given two writes that find the same database missing both create it, and one of them loses
        val client =
            clientAnswering {
                answer(HttpMethod.PUT, couchDbError(HttpStatus.PRECONDITION_FAILED, DATABASE_EXISTS_BODY), path = "/db")
            }

        // When / Then
        assertThatCode { client.createDatabase("db") }.doesNotThrowAnyException()
    }

    @Test
    fun `still fails creating a database CouchDB refuses`() {
        // Given
        val client =
            clientAnswering {
                answer(HttpMethod.PUT, couchDbError(HttpStatus.UNAUTHORIZED, UNAUTHORIZED_BODY), path = "/db")
            }

        // When
        val thrown = catchThrowable { client.createDatabase("db") }

        // Then
        assertThat(thrown)
            .isInstanceOf(ExternalSystemException::class.java)
            .extracting { (it as ExternalSystemException).code }
            .isEqualTo(DefaultCouchDbClientErrorCode.OTHER_COUCHDB_ERROR)
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
        }.isInstanceOf(ExternalSystemException::class.java)
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
