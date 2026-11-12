package com.aamdigital.aambackendservice.notification.core.config

import com.aamdigital.aambackendservice.common.condition.DocumentConditionEngine
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class PublicFormNotificationConfigCacheTest {
    @Mock
    lateinit var couchDbClient: CouchDbClient

    private val objectMapper = jacksonObjectMapper()
    private val documentConditionEngine = DocumentConditionEngine()
    private lateinit var cache: PublicFormNotificationConfigCache

    @BeforeEach
    fun setUp() {
        cache = PublicFormNotificationConfigCache(couchDbClient = couchDbClient, objectMapper = objectMapper)
    }

    private fun formDoc(json: String): ObjectNode = objectMapper.readTree(json.trimIndent()).deepCopy()

    private fun givenStoredForms(vararg docs: ObjectNode) {
        whenever(
            couchDbClient.getDatabaseDocumentsByPrefix(
                database = eq("app"),
                prefix = eq("PublicFormConfig"),
                kClass = eq(ObjectNode::class)
            )
        ).thenReturn(docs.toList())
    }

    private fun recordCreatedBy(by: String) =
        mapOf(
            "_id" to "Child:1",
            "email" to "someone@example.org",
            "created" to mapOf("at" to "2026-01-01", "by" to by)
        )

    @Test
    fun `should derive one email-only rule that matches only records created through that form`() {
        // given
        givenStoredForms(
            formDoc(
                """
                {
                    "_id": "PublicFormConfig:form-1",
                    "title": "Event registration",
                    "entity": "Child",
                    "columns": [{"fields": ["name", "email"]}],
                    "sendConfirmationEmail": true,
                    "confirmationEmailField": "email"
                }
                """
            )
        )

        // when
        val entries = cache.findAll()

        // then
        assertThat(entries).hasSize(1)
        val entry = entries.single()
        assertThat(entry.isExternalRecipient).isTrue()
        assertThat(entry.recipientEmailField).isEqualTo("email")
        assertThat(entry.channelEmail).isTrue()
        assertThat(entry.channelPush).isFalse()

        val rule = entry.rules.single()
        assertThat(rule.entityType).isEqualTo("Child")
        assertThat(rule.changeType).isEqualTo("created")
        assertThat(rule.label).isEqualTo("Submission received: Event registration")

        // the condition is what keeps records staff create in the normal UI from being confirmed
        assertThat(documentConditionEngine.matches(rule.conditions, recordCreatedBy("PublicForm:form-1"))).isTrue()
        assertThat(documentConditionEngine.matches(rule.conditions, recordCreatedBy("PublicForm:form-2"))).isFalse()
        assertThat(documentConditionEngine.matches(rule.conditions, recordCreatedBy("some-user-id"))).isFalse()
    }

    @Test
    fun `should take the default language of a multilingual title`() {
        // given
        givenStoredForms(
            formDoc(
                """
                {
                    "_id": "PublicFormConfig:form-1",
                    "title": {"de": "Anmeldung", "en": "Registration"},
                    "entity": "Child",
                    "sendConfirmationEmail": true,
                    "confirmationEmailField": "email"
                }
                """
            )
        )

        // when / then
        assertThat(
            cache
                .findAll()
                .single()
                .rules
                .single()
                .label
        ).isEqualTo("Submission received: Registration")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"_id": "PublicFormConfig:form-1", "entity": "Child", "sendConfirmationEmail": false, "confirmationEmailField": "email"}""",
            """{"_id": "PublicFormConfig:form-1", "entity": "Child", "confirmationEmailField": "email"}""",
            """{"_id": "PublicFormConfig:form-1", "entity": "Child", "sendConfirmationEmail": true}""",
            """{"_id": "PublicFormConfig:form-1", "entity": "Child", "sendConfirmationEmail": true, "confirmationEmailField": " "}""",
            """{"_id": "PublicFormConfig:form-1", "sendConfirmationEmail": true, "confirmationEmailField": "email"}"""
        ]
    )
    fun `should yield no rule for a form that does not confirm, or cannot say where to`(json: String) {
        // given
        givenStoredForms(formDoc(json))

        // when / then
        assertThat(cache.findAll()).isEmpty()
    }

    @Test
    fun `should drop the rule when the form stops confirming submissions`() {
        // given a form that confirmed submissions when the cache was loaded
        givenStoredForms(
            formDoc(
                """
                {"_id": "PublicFormConfig:form-1", "entity": "Child", "sendConfirmationEmail": true, "confirmationEmailField": "email"}
                """
            )
        )
        assertThat(cache.findAll()).hasSize(1)

        // and the admin then switches the confirmation off
        whenever(
            couchDbClient.getDatabaseDocument(
                database = eq("app"),
                documentId = eq("PublicFormConfig:form-1"),
                queryParams = any(),
                kClass = eq(PublicFormConfigDto::class)
            )
        ).thenReturn(
            PublicFormConfigDto(
                id = "PublicFormConfig:form-1",
                title = null,
                entity = "Child",
                sendConfirmationEmail = false,
                confirmationEmailField = "email"
            )
        )

        // when
        cache.refreshConfig(database = "app", notificationConfigId = "PublicFormConfig:form-1", deleted = false)

        // then the old rule must not keep matching
        assertThat(cache.findAll()).isEmpty()
    }
}
