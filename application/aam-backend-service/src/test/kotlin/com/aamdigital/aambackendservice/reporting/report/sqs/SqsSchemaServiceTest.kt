package com.aamdigital.aambackendservice.reporting.report.sqs

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.common.domain.TestErrorCode
import com.aamdigital.aambackendservice.common.error.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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
        whenever(
            couchDbClient.getDatabaseDocument(
                database = eq("app"),
                documentId = eq("Config:CONFIG_ENTITY"),
                queryParams = any(),
                kClass = eq(AppConfigFile::class)
            )
        ).thenReturn(AppConfigFile(id = "Config:CONFIG_ENTITY", rev = "1-abc", data = data))
        whenever(
            couchDbClient.getDatabaseDocument(
                database = eq("app"),
                documentId = eq("_design/sqlite:config"),
                queryParams = any(),
                kClass = eq(SqsSchema::class)
            )
        ).thenAnswer { throw NotFoundException(message = "not found", code = TestErrorCode.TEST_EXCEPTION) }
        whenever(
            couchDbClient.putDatabaseDocument(any(), any(), any())
        ).thenReturn(DocSuccess(ok = true, id = "_design/sqlite:config", rev = "1-new"))
    }

    private fun capturePublishedSchema(): SqsSchema {
        val bodyCaptor = argumentCaptor<Any>()
        verify(couchDbClient).putDatabaseDocument(
            database = eq("app"),
            documentId = eq("_design/sqlite:config"),
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
                        label = "Child",
                        attributes = mapOf("name" to AppConfigAttribute(dataType = "string"))
                    )
            )
        )

        // when
        service.updateSchema()

        // then
        val schema = capturePublishedSchema()
        val childFields = schema.sql.tables.getValue("Child").fields

        assertThat(childFields.getValue("_created_at").field).isEqualTo("created.at")
        assertThat(childFields.getValue("_created_by").field).isEqualTo("created.by")
        assertThat(childFields.getValue("_updated_at").field).isEqualTo("updated.at")
        assertThat(childFields.getValue("_updated_by").field).isEqualTo("updated.by")
        // the old unprefixed column names are gone
        assertThat(childFields).doesNotContainKeys("created_at", "created_by", "updated_at", "updated_by")
        // real entity fields stay unprefixed
        assertThat(childFields).containsKeys("name", "inactive", "anonymized")

        val enumFields = schema.sql.tables.getValue("ConfigurableEnum").fields
        assertThat(enumFields.getValue("values").field).isEqualTo("values")
        assertThat(enumFields).containsKey("_id")

        assertThat(schema.sql.indexes).containsExactly(SqsSchemaService.CONFIGURABLE_ENUM_OPTION_VIEW)
    }

    @Test
    fun `should override a manually configured ConfigurableEnum entity with the hard-wired table`() {
        // given: old workaround where entity:ConfigurableEnum was added to the config document
        stubConfig(
            mapOf(
                "entity:ConfigurableEnum" to
                    AppConfigEntry(
                        label = "ConfigurableEnum",
                        attributes = mapOf("values" to AppConfigAttribute(dataType = ""))
                    )
            )
        )

        // when
        service.updateSchema()

        // then: hard-wired definition wins, no metadata columns are mixed in
        val enumFields = capturePublishedSchema().sql.tables.getValue("ConfigurableEnum").fields
        assertThat(enumFields.keys).containsExactlyInAnyOrder("_id", "values")
    }
}
