package com.aamdigital.aambackendservice.common.couchdb.core

import com.aamdigital.aambackendservice.common.couchdb.core.DefaultCouchDbClient.Companion.FIND_PAGE_SIZE
import com.aamdigital.aambackendservice.common.couchdb.dto.FindResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.web.client.RestClient

class DefaultCouchDbClientTest {
    private val couchDbClient = spy(DefaultCouchDbClient(mock<RestClient>(), ObjectMapper()))

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
}
