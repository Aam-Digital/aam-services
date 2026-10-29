package com.aamdigital.aambackendservice.reporting.report.storage

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.TestErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import com.aamdigital.aambackendservice.reporting.report.ReportItem
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorageErrorCode
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
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

    @Test
    fun `should parse canonical doc with a multi-lingual title`() {
        // given
        stubFetchDoc(
            """
            {
              "_id": "ReportConfig:test",
              "_rev": "2-def",
              "title": { "de": "Bericht", "en-US": "Report" },
              "mode": "sql",
              "reportDefinition": [ { "query": "SELECT * FROM foo" } ]
            }
            """.trimIndent()
        )

        // when
        val report = storage.fetchReport(DomainReference("ReportConfig:test"))

        // then
        assertThat(report.title).isEqualTo("Report")
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
        val bodyCaptor = argumentCaptor<Any>()
        verify(couchDbClient).putDatabaseDocument(
            database = eq("app"),
            documentId = eq("ReportConfig:legacy"),
            body = bodyCaptor.capture()
        )

        @Suppress("UNCHECKED_CAST")
        val body = bodyCaptor.firstValue as Map<String, Any?>
        // CouchDB rejects unknown underscore-prefixed top-level members (doc_validation),
        // so the migrated body must not introduce any beyond the reserved "_id".
        assertThat(body.keys.filter { it.startsWith("_") && it != "_id" }).isEmpty()
        assertThat(body).containsKey("legacyOriginal")
    }

    @Test
    fun `should keep all translations of a multi-lingual title when writing back legacy docs`() {
        // given
        stubFetchDoc(
            """
            {
              "_id": "ReportConfig:legacy",
              "_rev": "1-abc",
              "title": { "en-US": "Legacy", "de": "Alt" },
              "mode": "sql",
              "aggregationDefinition": "SELECT * FROM foo"
            }
            """.trimIndent()
        )
        stubWriteBack()

        // when
        val report = storage.fetchReport(DomainReference("ReportConfig:legacy"))

        // then
        assertThat(report.title).isEqualTo("Legacy")
        val bodyCaptor = argumentCaptor<Any>()
        verify(couchDbClient).putDatabaseDocument(
            database = eq("app"),
            documentId = eq("ReportConfig:legacy"),
            body = bodyCaptor.capture()
        )

        @Suppress("UNCHECKED_CAST")
        val body = bodyCaptor.firstValue as Map<String, Any?>
        assertThat(objectMapper.valueToTree<ObjectNode>(body).get("title"))
            .isEqualTo(parseDoc("""{ "en-US": "Legacy", "de": "Alt" }"""))
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

    // --- Canonical doc with extra fields (e.g. previously migrated with legacyOriginal or version field) ---

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
              "legacyOriginal": {
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

    // --- Deprecated metadata column rename ---

    @Test
    fun `should rewrite deprecated metadata columns on read and write renamed doc back`() {
        // given
        stubFetchDoc(
            """
            {
              "_id": "ReportConfig:rename",
              "_rev": "1-abc",
              "title": "Rename",
              "mode": "sql",
              "reportDefinition": [
                { "query": "SELECT name, created_at FROM Child" }
              ]
            }
            """.trimIndent()
        )
        stubWriteBack()

        // when
        val report = storage.fetchReport(DomainReference("ReportConfig:rename"))

        // then
        assertThat((report.items[0] as ReportItem.ReportQuery).sql)
            .isEqualTo("SELECT name, _created_at FROM Child")

        val bodyCaptor = argumentCaptor<Any>()
        verify(couchDbClient).putDatabaseDocument(
            database = eq("app"),
            documentId = eq("ReportConfig:rename"),
            body = bodyCaptor.capture()
        )
        val writtenDoc = bodyCaptor.firstValue as ObjectNode
        val writtenQuery =
            writtenDoc
                .get("reportDefinition")
                .get(0)
                .get("query")
                .textValue()
        assertThat(writtenQuery).isEqualTo("SELECT name, _created_at FROM Child")
        // untouched fields survive the write-back
        assertThat(writtenDoc.get("_rev").textValue()).isEqualTo("1-abc")
        assertThat(writtenDoc.get("title").textValue()).isEqualTo("Rename")
    }

    // --- Invalid reportDefinition entries ---

    @Test
    fun `should reject an entry with an empty query as invalid input naming its position`() {
        // given
        stubFetchDoc(
            """
            {
              "_id": "ReportConfig:invalid",
              "_rev": "2-def",
              "title": "Invalid",
              "mode": "sql",
              "reportDefinition": [
                { "query": "SELECT name FROM Child" },
                { "query": "" }
              ]
            }
            """.trimIndent()
        )

        // when
        val ex =
            assertThrows<InvalidArgumentException> {
                storage.fetchReport(DomainReference("ReportConfig:invalid"))
            }

        // then
        assertThat(ex.code).isEqualTo(ReportStorageErrorCode.INVALID_REPORT_CONFIG)
        assertThat(ex.message).contains("ReportConfig:invalid", "reportDefinition[1]")
    }

    @Test
    fun `should reject an invalid entry nested in a group naming its position`() {
        // given
        stubFetchDoc(
            """
            {
              "_id": "ReportConfig:invalid",
              "_rev": "2-def",
              "title": "Invalid",
              "mode": "sql",
              "reportDefinition": [
                {
                  "groupTitle": "Children",
                  "items": [
                    { "query": "SELECT name FROM Child" },
                    { "groupTitle": "Empty group", "items": [] }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        // when
        val ex =
            assertThrows<InvalidArgumentException> {
                storage.fetchReport(DomainReference("ReportConfig:invalid"))
            }

        // then
        assertThat(ex.message).contains("reportDefinition[0].items[1]")
    }

    @Test
    fun `should reject a group without title`() {
        // given
        stubFetchDoc(
            """
            {
              "_id": "ReportConfig:invalid",
              "_rev": "2-def",
              "title": "Invalid",
              "mode": "sql",
              "reportDefinition": [
                { "items": [ { "query": "SELECT name FROM Child" } ] }
              ]
            }
            """.trimIndent()
        )

        // when/then
        assertThrows<InvalidArgumentException> {
            storage.fetchReport(DomainReference("ReportConfig:invalid"))
        }
    }

    @Test
    fun `should reject a null entry nested in a group naming its position`() {
        // given
        stubFetchDoc(
            """
            {
              "_id": "ReportConfig:invalid",
              "_rev": "2-def",
              "title": "Invalid",
              "mode": "sql",
              "reportDefinition": [
                { "groupTitle": "Children", "items": [ null ] }
              ]
            }
            """.trimIndent()
        )

        // when
        val ex =
            assertThrows<InvalidArgumentException> {
                storage.fetchReport(DomainReference("ReportConfig:invalid"))
            }

        // then
        assertThat(ex.message).contains("reportDefinition[0].items[0]")
    }

    @Test
    fun `should name the report and field when an entry has the wrong type`() {
        // given
        stubFetchDoc(
            """
            {
              "_id": "ReportConfig:invalid",
              "_rev": "2-def",
              "title": "Invalid",
              "mode": "sql",
              "reportDefinition": [ { "query": { "sql": "SELECT 1" } } ]
            }
            """.trimIndent()
        )

        // when
        val ex =
            assertThrows<InvalidArgumentException> {
                storage.fetchReport(DomainReference("ReportConfig:invalid"))
            }

        // then
        assertThat(ex.code).isEqualTo(ReportStorageErrorCode.PARSING_ERROR)
        assertThat(ex.message).contains("ReportConfig:invalid", "reportDefinition", "query")
    }

    // --- Fetching all reports ---

    @Test
    fun `should skip an invalid config when fetching all reports and return the valid ones`() {
        // given
        whenever(
            couchDbClient.findDatabaseDocumentsByPrefix(
                database = any(),
                prefix = any(),
                selector = any(),
                limit = anyOrNull(),
                kClass = eq(ObjectNode::class)
            )
        ).thenReturn(
            listOf(
                parseDoc(
                    """
                    {
                      "_id": "ReportConfig:valid",
                      "_rev": "2-def",
                      "title": "Valid",
                      "mode": "sql",
                      "reportDefinition": [ { "query": "SELECT name FROM Child" } ]
                    }
                    """.trimIndent()
                ),
                parseDoc(
                    """
                    {
                      "_id": "ReportConfig:invalid",
                      "_rev": "2-def",
                      "title": "Invalid",
                      "mode": "sql",
                      "reportDefinition": [ { "query": "" } ]
                    }
                    """.trimIndent()
                ),
                parseDoc(
                    """
                    {
                      "_id": "ReportConfig:null-entry",
                      "_rev": "2-def",
                      "title": "Null entry",
                      "mode": "sql",
                      "reportDefinition": [ null ]
                    }
                    """.trimIndent()
                )
            )
        )

        // when
        val reports = storage.fetchAllReports("sql")

        // then
        assertThat(reports.map { it.id }).containsExactly("ReportConfig:valid")
    }

    @Test
    fun `should propagate storage failures when fetching all reports`() {
        // given
        whenever(
            couchDbClient.findDatabaseDocumentsByPrefix(
                database = any(),
                prefix = any(),
                selector = any(),
                limit = anyOrNull(),
                kClass = eq(ObjectNode::class)
            )
        ).thenAnswer { throw ExternalSystemException(message = "CouchDB down", code = TestErrorCode.TEST_EXCEPTION) }

        // when/then
        assertThrows<ExternalSystemException> {
            storage.fetchAllReports("sql")
        }
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
