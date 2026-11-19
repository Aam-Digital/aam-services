package com.aamdigital.aambackendservice.notification.repository

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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class CouchDbEntityTypeLabelProviderTest {
    private lateinit var provider: CouchDbEntityTypeLabelProvider

    @Mock
    lateinit var couchDbClient: CouchDbClient

    @BeforeEach
    fun setUp() {
        provider = CouchDbEntityTypeLabelProvider(couchDbClient)
    }

    private fun fetchConfig() =
        couchDbClient.getDatabaseDocument(
            database = eq("app"),
            documentId = eq("Config:CONFIG_ENTITY"),
            queryParams = any(),
            kClass = eq(JsonNode::class)
        )

    private fun config(json: String): JsonNode = ObjectMapper().readTree(json)

    @Test
    fun `should return the configured label of the entity type`() {
        // given
        whenever(fetchConfig()).thenReturn(config("""{ "data": { "entity:Child": { "label": "Participant" } } }"""))

        // when
        val label = provider.labelFor("Child")

        // then
        assertThat(label).isEqualTo("Participant")
    }

    @Test
    fun `should resolve a multi-lingual label`() {
        // given
        whenever(fetchConfig()).thenReturn(
            config("""{ "data": { "entity:Child": { "label": { "de": "Teilnehmer", "en": "Participant" } } } }""")
        )

        // when
        val label = provider.labelFor("Child")

        // then
        assertThat(label).isEqualTo("Participant")
    }

    @Test
    fun `should return null if the entity type has no label configured`() {
        // given
        whenever(fetchConfig()).thenReturn(
            config("""{ "data": { "entity:Child": { "attributes": {} }, "entity:School": { "label": " " } } }""")
        )

        // when / then
        assertThat(provider.labelFor("Child")).isNull()
        assertThat(provider.labelFor("School")).isNull()
        assertThat(provider.labelFor("Note")).isNull()
    }

    @Test
    fun `should return null if there is no entity config`() {
        // given
        whenever(fetchConfig()).thenAnswer {
            throw NotFoundException(message = "not found", code = TestErrorCode.TEST_EXCEPTION)
        }

        // when
        val label = provider.labelFor("Child")

        // then
        assertThat(label).isNull()
    }

    @Test
    fun `should return null if the entity config cannot be read and try again on the next lookup`() {
        // given
        whenever(fetchConfig())
            .thenAnswer { throw ExternalSystemException(message = "unreachable", code = TestErrorCode.TEST_EXCEPTION) }
            .thenReturn(config("""{ "data": { "entity:Child": { "label": "Participant" } } }"""))

        // when
        val first = provider.labelFor("Child")
        val second = provider.labelFor("Child")

        // then
        assertThat(first).isNull()
        assertThat(second).isEqualTo("Participant")
    }

    @Test
    fun `should read the entity config once and again only after it was invalidated`() {
        // given
        whenever(fetchConfig())
            .thenReturn(config("""{ "data": { "entity:Child": { "label": "Participant" } } }"""))
            .thenReturn(config("""{ "data": { "entity:Child": { "label": "Beneficiary" } } }"""))

        // when
        val beforeChange = listOf(provider.labelFor("Child"), provider.labelFor("Child"))
        provider.invalidate()
        val afterChange = provider.labelFor("Child")

        // then
        assertThat(beforeChange).containsExactly("Participant", "Participant")
        assertThat(afterChange).isEqualTo("Beneficiary")
        verify(couchDbClient, times(2)).getDatabaseDocument(any(), any(), any(), eq(JsonNode::class))
    }
}
