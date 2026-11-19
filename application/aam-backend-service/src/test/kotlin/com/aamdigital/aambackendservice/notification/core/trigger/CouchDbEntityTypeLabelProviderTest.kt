package com.aamdigital.aambackendservice.notification.core.trigger

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.domain.TestErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class CouchDbEntityTypeLabelProviderTest {
    private lateinit var provider: CouchDbEntityTypeLabelProvider

    @Mock
    lateinit var couchDbClient: CouchDbClient

    @BeforeEach
    fun setUp() {
        reset(couchDbClient)
        provider = CouchDbEntityTypeLabelProvider(couchDbClient)
    }

    private fun stubConfig(json: String) {
        whenever(
            couchDbClient.getDatabaseDocument(
                database = eq("app"),
                documentId = eq("Config:CONFIG_ENTITY"),
                queryParams = any(),
                kClass = eq(JsonNode::class)
            )
        ).thenReturn(ObjectMapper().readTree(json))
    }

    @Test
    fun `should return the configured label of the entity type`() {
        stubConfig("""{ "data": { "entity:Child": { "label": "Participant" } } }""")

        assertThat(provider.labelFor("Child")).isEqualTo("Participant")
    }

    @Test
    fun `should resolve a multi-lingual label`() {
        stubConfig("""{ "data": { "entity:Child": { "label": { "de": "Teilnehmer", "en": "Participant" } } } }""")

        assertThat(provider.labelFor("Child")).isEqualTo("Participant")
    }

    @Test
    fun `should return null if the entity type has no label configured`() {
        stubConfig("""{ "data": { "entity:Child": { "attributes": {} }, "entity:School": { "label": " " } } }""")

        assertThat(provider.labelFor("Child")).isNull()
        assertThat(provider.labelFor("School")).isNull()
        assertThat(provider.labelFor("Note")).isNull()
    }

    @Test
    fun `should return null if the entity config cannot be read`() {
        for (exception in listOf(
            NotFoundException(message = "not found", code = TestErrorCode.TEST_EXCEPTION),
            ExternalSystemException(message = "unreachable", code = TestErrorCode.TEST_EXCEPTION)
        )) {
            whenever(
                couchDbClient.getDatabaseDocument(any(), any(), any(), eq(JsonNode::class))
            ).thenAnswer { throw exception }

            assertThat(provider.labelFor("Child")).isNull()
        }
    }
}
