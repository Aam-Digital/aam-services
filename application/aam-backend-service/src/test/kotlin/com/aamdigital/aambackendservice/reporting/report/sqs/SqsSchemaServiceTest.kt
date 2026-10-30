package com.aamdigital.aambackendservice.reporting.report.sqs

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.DefaultCouchDbClient.DefaultCouchDbClientErrorCode
import com.aamdigital.aambackendservice.common.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.common.domain.TestErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.common.rest.ObjectMapperConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val SCHEMA_DOC_ID = "_design/sqlite:config"

@ExtendWith(MockitoExtension::class)
class SqsSchemaServiceTest {
    private lateinit var service: SqsSchemaService

    @Mock
    lateinit var couchDbClient: CouchDbClient

    @BeforeEach
    fun setUp() {
        reset(couchDbClient)
        service = SqsSchemaService(couchDbClient)
    }

    private fun stubConfig(data: Map<String, AppConfigEntry>) {
        stubConfigDocument(data)
        whenever(
            couchDbClient.getDatabaseDocument(
                database = eq("app"),
                documentId = eq(SCHEMA_DOC_ID),
                queryParams = any(),
                kClass = eq(SqsSchema::class)
            )
        ).thenAnswer { throw NotFoundException(message = "not found", code = TestErrorCode.TEST_EXCEPTION) }
        whenever(
            couchDbClient.putDatabaseDocument(any(), any(), any())
        ).thenReturn(DocSuccess(ok = true, id = SCHEMA_DOC_ID, rev = "1-new"))
    }

    private fun stubConfigDocument(data: Map<String, AppConfigEntry>) {
        whenever(
            couchDbClient.getDatabaseDocument(
                database = eq("app"),
                documentId = eq("Config:CONFIG_ENTITY"),
                queryParams = any(),
                kClass = eq(AppConfigFile::class)
            )
        ).thenReturn(AppConfigFile(id = "Config:CONFIG_ENTITY", rev = "1-abc", data = data))
    }

    /** Backs the schema document by [stored], so reads see what was written before. */
    private fun stubSchemaDocument(
        stored: AtomicReference<SqsSchema?>,
        onRead: () -> Unit = {}
    ) {
        whenever(
            couchDbClient.getDatabaseDocument(
                database = eq("app"),
                documentId = eq(SCHEMA_DOC_ID),
                queryParams = any(),
                kClass = eq(SqsSchema::class)
            )
        ).thenAnswer {
            onRead()
            stored.get() ?: throw NotFoundException(message = "not found", code = TestErrorCode.TEST_EXCEPTION)
        }
    }

    private fun capturePublishedSchema(): SqsSchema {
        val bodyCaptor = argumentCaptor<Any>()
        verify(couchDbClient).putDatabaseDocument(
            database = eq("app"),
            documentId = eq(SCHEMA_DOC_ID),
            body = bodyCaptor.capture()
        )
        return bodyCaptor.firstValue as SqsSchema
    }

    @Test
    fun `should emit prefixed metadata columns, hard-wired ConfigurableEnum table and option view`() {
        // given
        stubConfig(
            mapOf(
                "entity:Child" to
                    AppConfigEntry(
                        attributes = mapOf("name" to AppConfigAttribute(dataType = "string"))
                    )
            )
        )

        // when
        service.updateSchema()

        // then
        val schema = capturePublishedSchema()
        val childFields =
            schema.sql.tables
                .getValue("Child")
                .fields

        assertThat(childFields.getValue("_created_at").field).isEqualTo("created.at")
        assertThat(childFields.getValue("_created_by").field).isEqualTo("created.by")
        assertThat(childFields.getValue("_updated_at").field).isEqualTo("updated.at")
        assertThat(childFields.getValue("_updated_by").field).isEqualTo("updated.by")
        // the old unprefixed column names are gone
        assertThat(childFields).doesNotContainKeys("created_at", "created_by", "updated_at", "updated_by")
        // real entity fields stay unprefixed
        assertThat(childFields).containsKeys("name", "inactive", "anonymized")

        val enumFields =
            schema.sql.tables
                .getValue("ConfigurableEnum")
                .fields
        assertThat(enumFields.getValue("values").field).isEqualTo("values")
        assertThat(enumFields).containsKey("_id")

        assertThat(schema.sql.indexes).containsExactly(ConfigurableEnumSchema.OPTION_VIEW)
    }

    @Test
    fun `should override a manually configured ConfigurableEnum entity with the hard-wired table`() {
        // given: old workaround where entity:ConfigurableEnum was added to the config document
        stubConfig(
            mapOf(
                "entity:ConfigurableEnum" to
                    AppConfigEntry(
                        attributes = mapOf("values" to AppConfigAttribute(dataType = ""))
                    )
            )
        )

        // when
        service.updateSchema()

        // then: hard-wired definition wins, no metadata columns are mixed in
        val enumFields =
            capturePublishedSchema()
                .sql.tables
                .getValue("ConfigurableEnum")
                .fields
        assertThat(enumFields.keys).containsExactlyInAnyOrder("_id", "values")
    }

    @Test
    fun `should add SiteSettings table with default language for the enum option view`() {
        // given
        stubConfig(emptyMap())

        // when
        service.updateSchema()

        // then
        val siteSettingsFields =
            capturePublishedSchema()
                .sql.tables
                .getValue("SiteSettings")
                .fields
        assertThat(siteSettingsFields.keys).containsExactlyInAnyOrder("_id", "defaultLanguage")
    }

    @Test
    fun `should keep fields of a manually configured SiteSettings entity`() {
        // given
        stubConfig(
            mapOf(
                "entity:SiteSettings" to
                    AppConfigEntry(
                        attributes = mapOf("siteName" to AppConfigAttribute(dataType = "string"))
                    )
            )
        )

        // when
        service.updateSchema()

        // then
        val siteSettingsFields =
            capturePublishedSchema()
                .sql.tables
                .getValue("SiteSettings")
                .fields
        assertThat(siteSettingsFields).containsKeys("siteName", "defaultLanguage", "_created_at")
    }

    @Test
    fun `should parse config with translated labels of multi-lingual systems`() {
        // given
        val json =
            """
            {
              "_id": "Config:CONFIG_ENTITY",
              "_rev": "1-abc",
              "data": {
                "entity:Action": {
                  "label": { "en": "Action", "de": "Aktion" },
                  "attributes": {
                    "title": { "dataType": "string", "label": { "en": "Title", "de": "Titel" } }
                  }
                }
              }
            }
            """.trimIndent()

        // when
        val config = ObjectMapperConfiguration().objectMapper().readValue(json, AppConfigFile::class.java)

        // then
        assertThat(
            config.data
                ?.getValue("entity:Action")
                ?.attributes
                ?.getValue("title")
                ?.dataType
        ).isEqualTo("string")
    }

    @Test
    fun `should write a changed schema only once when calculations update it concurrently`() {
        // given: both calls read the schema before either writes it, unless the reads are serialised
        stubConfigDocument(emptyMap())
        val stored = AtomicReference<SqsSchema?>()
        val bothRead = CyclicBarrier(2)
        stubSchemaDocument(stored) {
            runCatching { bothRead.await(500, TimeUnit.MILLISECONDS) }
        }
        whenever(couchDbClient.putDatabaseDocument(any(), any(), any())).thenAnswer {
            stored.set(it.getArgument(2))
            DocSuccess(ok = true, id = SCHEMA_DOC_ID, rev = "1-new")
        }
        val executor = Executors.newFixedThreadPool(2)

        // when
        try {
            val calls = List(2) { executor.submit { service.updateSchema() } }
            calls.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        // then
        verify(couchDbClient, times(1)).putDatabaseDocument(any(), any(), any())
    }

    @Test
    fun `should accept a conflict when the same schema was written concurrently`() {
        // given
        stubConfigDocument(emptyMap())
        val stored = AtomicReference<SqsSchema?>()
        stubSchemaDocument(stored)
        whenever(couchDbClient.putDatabaseDocument(any(), any(), any())).thenAnswer {
            stored.set(it.getArgument(2))
            throw ExternalSystemException(message = "conflict", code = DefaultCouchDbClientErrorCode.CONFLICT)
        }

        // when / then
        assertThatCode { service.updateSchema() }.doesNotThrowAnyException()
    }

    @Test
    fun `should rethrow a conflict when a different schema was written concurrently`() {
        // given
        stubConfigDocument(emptyMap())
        val stored = AtomicReference<SqsSchema?>()
        stubSchemaDocument(stored)
        whenever(couchDbClient.putDatabaseDocument(any(), any(), any())).thenAnswer {
            stored.set(
                SqsSchema(
                    sql =
                        SqlObject(
                            tables = emptyMap(),
                            indexes = null,
                            options = SqlOptions(TableName(field = "_id", separator = ":"))
                        )
                )
            )
            throw ExternalSystemException(message = "conflict", code = DefaultCouchDbClientErrorCode.CONFLICT)
        }

        // when / then
        assertThatThrownBy { service.updateSchema() }
            .isInstanceOf(ExternalSystemException::class.java)
            .hasFieldOrPropertyWithValue("code", DefaultCouchDbClientErrorCode.CONFLICT)
    }
}
