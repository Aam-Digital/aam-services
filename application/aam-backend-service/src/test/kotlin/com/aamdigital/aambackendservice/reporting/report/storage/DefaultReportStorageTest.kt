package com.aamdigital.aambackendservice.reporting.report.storage

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.TestErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import com.aamdigital.aambackendservice.reporting.report.ReportItem
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class DefaultReportStorageTest {
    private lateinit var storage: DefaultReportStorage

    @Mock
    lateinit var couchDbClient: CouchDbClient

    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        reset(couchDbClient)
        storage = DefaultReportStorage(couchDbClient, objectMapper)
    }

    private fun parseDoc(json: String): ObjectNode = objectMapper.readTree(json) as ObjectNode

    private fun stubFetchDoc(docJson: String) {
        whenever(
            couchDbClient.getDatabaseDocument(
                database = any(),
                documentId = any(),
                queryParams = any(),
                kClass = eq(ObjectNode::class)
            )
        ).thenReturn(parseDoc(docJson))
    }

    private fun stubWriteBack() {
        whenever(
            couchDbClient.putDatabaseDocument(
                database = any(),
                documentId = any(),
                body = any()
            )
        ).thenReturn(DocSuccess(ok = true, id = "ReportConfig:test", rev = "2-migrated"))
    }

    // --- Legacy v1 positional placeholders ---

    @Test
    fun `should normalize legacy v1 with positional placeholders to canonical form`() {
        // given
        stubFetchDoc(
            """
            {
              "_id": "ReportConfig:test",
              "_rev": "1-abc",
              "title": "Test",
              "mode": "sql",
              "neededArgs": ["from", "to"],
              "aggregationDefinition": "SELECT * FROM foo WHERE d BETWEEN ? AND ?"
            }
            """.trimIndent()
        )
        stubWriteBack()

        // when
        val report = storage.fetchReport(DomainReference("ReportConfig:test"))

        // then
        assertThat(report.id).isEqualTo("ReportConfig:test")
        assertThat(report.transformations).isEqualTo(
            mapOf("startDate" to listOf("SQL_FROM_DATE"), "endDate" to listOf("SQL_TO_DATE"))
        )
        assertThat(report.items).hasSize(1)
        val query = report.items[0] as ReportItem.ReportQuery
        assertThat(query.sql).isEqualTo("SELECT * FROM foo WHERE d BETWEEN \$startDate AND \$endDate")
    }

    // --- Legacy v1 named placeholders ($from/$to) ---

    @Test
    fun `should normalize legacy v1 with named legacy placeholders to canonical form`() {
        // given
        stubFetchDoc(
            """
            {
              "_id": "ReportConfig:test",
              "_rev": "1-abc",
              "title": "Test",
              "mode": "sql",
              "version": 1,
              "neededArgs": ["from", "to"],
              "aggregationDefinition": "SELECT * FROM foo WHERE d BETWEEN ${"$"}from AND ${"$"}to AND d BETWEEN ${"$"}from AND ${"$"}to"
            }
            """.trimIndent()
        )
        stubWriteBack()

        // when
        val report = storage.fetchReport(DomainReference("ReportConfig:test"))

        // then
        val query = report.items[0] as ReportItem.ReportQuery
        assertThat(query.sql).isEqualTo(
            "SELECT * FROM foo WHERE d BETWEEN \$startDate AND \$endDate AND d BETWEEN \$startDate AND \$endDate"
        )
        assertThat(report.transformations).isEqualTo(
            mapOf("startDate" to listOf("SQL_FROM_DATE"), "endDate" to listOf("SQL_TO_DATE"))
        )
    }

    // --- Legacy v1 with no args ---

    @Test
    fun `should normalize legacy v1 with no args and no placeholders`() {
        // given
        stubFetchDoc(
            """
            {
              "_id": "ReportConfig:test",
              "_rev": "1-abc",
              "title": "Test",
              "mode": "sql",
              "neededArgs": [],
              "aggregationDefinition": "SELECT name FROM foo"
            }
            """.trimIndent()
        )
        stubWriteBack()

        // when
        val report = storage.fetchReport(DomainReference("ReportConfig:test"))

        // then
        assertThat(report.transformations).isEmpty()
        val query = report.items[0] as ReportItem.ReportQuery
        assertThat(query.sql).isEqualTo("SELECT name FROM foo")
    }

    // --- Canonical doc (already migrated) ---

    @Test
    fun `should parse canonical doc without triggering write-back`() {
        // given
        stubFetchDoc(
            """
            {
              "_id": "ReportConfig:test",
              "_rev": "2-def",
              "title": "Test",
              "mode": "sql",
              "transformations": { "startDate": ["SQL_FROM_DATE"], "endDate": ["SQL_TO_DATE"] },
              "reportDefinition": [
                { "query": "SELECT * FROM foo WHERE d BETWEEN ${"$"}startDate AND ${"$"}endDate" }
              ]
            }
            """.trimIndent()
        )

        // when
        val report = storage.fetchReport(DomainReference("ReportConfig:test"))

        // then
        assertThat(report.transformations).isEqualTo(
            mapOf("startDate" to listOf("SQL_FROM_DATE"), "endDate" to listOf("SQL_TO_DATE"))
        )
        verify(couchDbClient, never()).putDatabaseDocument(any(), any(), any())
    }

    // --- Write-back migration ---

    @Test
    fun `should write migrated doc back to CouchDB for legacy docs`() {
        // given
        stubFetchDoc(
            """
            {
              "_id": "ReportConfig:legacy",
              "_rev": "1-abc",
              "title": "Legacy",
              "mode": "sql",
              "neededArgs": ["from", "to"],
              "aggregationDefinition": "SELECT * FROM foo WHERE d BETWEEN ? AND ?"
            }
            """.trimIndent()
        )
        stubWriteBack()

        // when
        storage.fetchReport(DomainReference("ReportConfig:legacy"))

        // then
        verify(couchDbClient).putDatabaseDocument(
            database = eq("app"),
            documentId = eq("ReportConfig:legacy"),
            body = any()
        )
    }

    @Test
    fun `should still return canonical report when write-back fails with conflict`() {
        // given
        stubFetchDoc(
            """
            {
              "_id": "ReportConfig:legacy",
              "_rev": "1-abc",
              "title": "Legacy",
              "mode": "sql",
              "neededArgs": ["from"],
              "aggregationDefinition": "SELECT * FROM foo WHERE d > ?"
            }
            """.trimIndent()
        )
        whenever(
            couchDbClient.putDatabaseDocument(any(), any(), any())
        ).thenAnswer { throw ExternalSystemException(message = "409 Conflict", code = TestErrorCode.TEST_EXCEPTION) }

        // when
        val report = storage.fetchReport(DomainReference("ReportConfig:legacy"))

        // then — report is returned even though write-back failed
        assertThat(report.id).isEqualTo("ReportConfig:legacy")
        assertThat(report.transformations).containsKey("startDate")
    }

    // --- Canonical doc with extra fields (e.g. previously migrated with _legacyOriginal or version field) ---

    @Test
    fun `should parse canonical doc with extra unknown fields without triggering write-back`() {
        // given
        stubFetchDoc(
            """
            {
              "_id": "ReportConfig:migrated",
              "_rev": "3-ghi",
              "title": "Migrated",
              "mode": "sql",
              "version": "2",
              "_legacyOriginal": {
                "aggregationDefinition": "SELECT * FROM foo WHERE d BETWEEN ? AND ?",
                "neededArgs": ["from", "to"]
              },
              "transformations": { "startDate": ["SQL_FROM_DATE"], "endDate": ["SQL_TO_DATE"] },
              "reportDefinition": [
                { "query": "SELECT * FROM foo WHERE d BETWEEN ${"$"}startDate AND ${"$"}endDate" }
              ]
            }
            """.trimIndent()
        )

        // when
        val report = storage.fetchReport(DomainReference("ReportConfig:migrated"))

        // then
        assertThat(report.id).isEqualTo("ReportConfig:migrated")
        assertThat(report.transformations).isEqualTo(
            mapOf("startDate" to listOf("SQL_FROM_DATE"), "endDate" to listOf("SQL_TO_DATE"))
        )
        val query = report.items[0] as ReportItem.ReportQuery
        assertThat(query.sql).isEqualTo("SELECT * FROM foo WHERE d BETWEEN \$startDate AND \$endDate")
        verify(couchDbClient, never()).putDatabaseDocument(any(), any(), any())
    }

    // --- Non-SQL report ---

    @Test
    fun `should throw InvalidArgumentException for non-sql report`() {
        // given
        stubFetchDoc(
            """
            {
              "_id": "ReportConfig:export",
              "_rev": "1-abc",
              "title": "Export",
              "mode": "exporting"
            }
            """.trimIndent()
        )

        // when/then
        assertThrows<InvalidArgumentException> {
            storage.fetchReport(DomainReference("ReportConfig:export"))
        }
    }
}
