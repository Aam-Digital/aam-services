package com.aamdigital.aambackendservice.notification.core.config

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.DefaultCouchDbClient.DefaultCouchDbClientErrorCode
import com.aamdigital.aambackendservice.common.couchdb.core.getEmptyQueryParams
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
class DefaultNotificationConfigCacheTest {
    @Mock
    lateinit var couchDbClient: CouchDbClient

    private val objectMapper = jacksonObjectMapper()
    private lateinit var cache: DefaultNotificationConfigCache

    @BeforeEach
    fun setUp() {
        cache =
            DefaultNotificationConfigCache(
                couchDbClient = couchDbClient,
                objectMapper = objectMapper
            )
    }

    @Test
    fun `should load notification configs from couchdb with one rule entry per change type`() {
        // given
        val configDoc =
            objectMapper
                .readTree(
                    """
                    {
                        "_id": "NotificationConfig:user-1",
                        "_rev": "1-abc",
                        "channels": {
                            "push": true,
                            "email": false
                        },
                        "notificationRules": [
                            {
                                "label": "Rule 1",
                                "notificationType": "entity_change",
                                "entityType": "Child",
                                "changeType": ["created", "updated"],
                                "conditions": {
                                    "${'$'}or": [
                                        {"name": {"${'$'}eq": "Bert"}},
                                        {"age": {"${'$'}not": {"${'$'}gte": 18}}}
                                    ]
                                },
                                "enabled": true
                            }
                        ]
                    }
                    """.trimIndent()
                ).deepCopy<ObjectNode>()

        whenever(
            couchDbClient.getDatabaseDocumentsByPrefix(
                database = eq("app"),
                prefix = eq("NotificationConfig"),
                kClass = eq(ObjectNode::class)
            )
        ).thenReturn(listOf(configDoc))

        // when
        val entries = cache.findAll()

        // then
        assertThat(entries).hasSize(1)
        assertThat(entries.first().userIdentifier).isEqualTo("user-1")
        // the `${'$'}or` is kept as one rule, so a document matching several branches notifies only once
        assertThat(entries.first().rules.map { it.changeType }).containsExactly("created", "updated")
        assertThat(entries.first().rules.map { it.externalIdentifier })
            .doesNotContainNull()
            .doesNotHaveDuplicates()
    }

    @Test
    fun `should skip only the rule with invalid conditions`() {
        // given
        val configDoc =
            objectMapper
                .readTree(
                    """
                    {
                        "_id": "NotificationConfig:user-1",
                        "_rev": "1-abc",
                        "notificationRules": [
                            {
                                "label": "Invalid rule",
                                "notificationType": "entity_change",
                                "entityType": "Child",
                                "changeType": ["created"],
                                "conditions": {"name": {"${'$'}unknownOperator": "Bert"}},
                                "enabled": true
                            },
                            {
                                "label": "Valid rule",
                                "notificationType": "entity_change",
                                "entityType": "Child",
                                "changeType": ["created"],
                                "conditions": {"name": {"${'$'}not": {"${'$'}eq": "Bert"}}},
                                "enabled": true
                            }
                        ]
                    }
                    """.trimIndent()
                ).deepCopy<ObjectNode>()

        whenever(
            couchDbClient.getDatabaseDocumentsByPrefix(
                database = eq("app"),
                prefix = eq("NotificationConfig"),
                kClass = eq(ObjectNode::class)
            )
        ).thenReturn(listOf(configDoc))

        // when
        val entries = cache.findAll()

        // then
        assertThat(entries).hasSize(1)
        assertThat(entries.first().rules.map { it.label }).containsExactly("Valid rule")
    }

    @Test
    fun `should derive the same rule identifier for the same conditions in a different key order`() {
        // given
        fun configWithConditions(conditions: String): ObjectNode =
            objectMapper
                .readTree(
                    """
                    {
                        "_id": "NotificationConfig:user-1",
                        "_rev": "1-abc",
                        "notificationRules": [
                            {
                                "label": "Rule",
                                "notificationType": "entity_change",
                                "entityType": "Child",
                                "changeType": ["created"],
                                "conditions": $conditions,
                                "enabled": true
                            }
                        ]
                    }
                    """.trimIndent()
                ).deepCopy<ObjectNode>()

        whenever(
            couchDbClient.getDatabaseDocumentsByPrefix(
                database = eq("app"),
                prefix = eq("NotificationConfig"),
                kClass = eq(ObjectNode::class)
            )
        ).thenReturn(
            listOf(configWithConditions("""{"name": "Bert", "age": 18}""")),
            listOf(configWithConditions("""{"age": 18, "name": "Bert"}"""))
        )

        // when
        val first =
            cache
                .findAll()
                .single()
                .rules
                .single()
                .externalIdentifier
        val otherCache = DefaultNotificationConfigCache(couchDbClient = couchDbClient, objectMapper = objectMapper)
        val second =
            otherCache
                .findAll()
                .single()
                .rules
                .single()
                .externalIdentifier

        // then
        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `should refresh one notification config in cache`() {
        // given the cache has already been loaded, with no configs yet
        cache.findAll()
        whenever(
            couchDbClient.getDatabaseDocument(
                database = eq("app"),
                documentId = eq("NotificationConfig:user-1"),
                queryParams = eq(getEmptyQueryParams()),
                kClass = eq(NotificationConfigDto::class)
            )
        ).thenReturn(
            NotificationConfigDto(
                id = "NotificationConfig:user-1",
                rev = "1-abc",
                channels = NotificationChannelConfig(push = true, email = true),
                notificationRules =
                    listOf(
                        NotificationRuleDto(
                            label = "Rule",
                            notificationType = NotificationType.ENTITY_CHANGE,
                            entityType = "Child",
                            changeType = listOf("created"),
                            conditions = objectMapper.readTree("{}"),
                            enabled = true
                        )
                    )
            )
        )

        // when
        cache.refreshConfig(
            database = "app",
            notificationConfigId = "NotificationConfig:user-1",
            deleted = false
        )

        // then
        val entries = cache.findAll()
        assertThat(entries).hasSize(1)
        assertThat(entries.first().userIdentifier).isEqualTo("user-1")
        assertThat(entries.first().channelEmail).isTrue
    }

    @Test
    fun `should remove notification config from cache when deleted`() {
        // given the cache has already been loaded, with no configs yet
        cache.findAll()
        whenever(
            couchDbClient.getDatabaseDocument(
                database = eq("tenant-db"),
                documentId = eq("NotificationConfig:user-1"),
                queryParams = any(),
                kClass = eq(NotificationConfigDto::class)
            )
        ).thenReturn(
            NotificationConfigDto(
                id = "NotificationConfig:user-1",
                rev = "1-abc",
                channels = NotificationChannelConfig(push = true, email = false),
                notificationRules = emptyList()
            )
        )

        cache.refreshConfig(
            database = "tenant-db",
            notificationConfigId = "NotificationConfig:user-1",
            deleted = false
        )
        assertThat(cache.findAll()).hasSize(1)

        // when
        cache.refreshConfig(
            database = "tenant-db",
            notificationConfigId = "NotificationConfig:user-1",
            deleted = true
        )

        // then
        assertThat(cache.findAll()).isEmpty()
    }

    @Test
    fun `should remove notification config when refresh fetch returns not found`() {
        // given the cache has already been loaded, with no configs yet
        cache.findAll()
        whenever(
            couchDbClient.getDatabaseDocument(
                database = eq("tenant-db"),
                documentId = eq("NotificationConfig:user-1"),
                queryParams = any(),
                kClass = eq(NotificationConfigDto::class)
            )
        ).thenReturn(
            NotificationConfigDto(
                id = "NotificationConfig:user-1",
                rev = "1-abc",
                channels = NotificationChannelConfig(push = true, email = false),
                notificationRules = emptyList()
            )
        )

        cache.refreshConfig(
            database = "tenant-db",
            notificationConfigId = "NotificationConfig:user-1",
            deleted = false
        )
        assertThat(cache.findAll()).hasSize(1)

        whenever(
            couchDbClient.getDatabaseDocument(
                database = eq("tenant-db"),
                documentId = eq("NotificationConfig:user-1"),
                queryParams = any(),
                kClass = eq(NotificationConfigDto::class)
            )
        ).thenThrow(NotFoundException("not found", code = DefaultCouchDbClientErrorCode.NOT_FOUND))

        // when
        cache.refreshConfig(
            database = "tenant-db",
            notificationConfigId = "NotificationConfig:user-1",
            deleted = false
        )

        // then
        assertThat(cache.findAll()).isEmpty()
    }

    @Test
    fun `should load all notification configs on first use, and only once`() {
        // given nothing loads the cache ahead of the first change, so nothing can race it
        whenever(
            couchDbClient.getDatabaseDocumentsByPrefix(
                database = eq("app"),
                prefix = eq("NotificationConfig"),
                kClass = eq(ObjectNode::class)
            )
        ).thenReturn(listOf(configDoc("user-1")))

        // when
        val first = cache.findAll()
        val second = cache.findAll()

        // then
        assertThat(first.map { it.userIdentifier }).containsExactly("user-1")
        assertThat(second).isEqualTo(first)
        verify(couchDbClient, times(1)).getDatabaseDocumentsByPrefix(any(), any(), eq(ObjectNode::class))
    }

    @Test
    fun `should fail instead of reporting no configs when loading fails, and load again next time`() {
        // given an empty result would read as "no rule matched" and drop the change's notifications
        whenever(
            couchDbClient.getDatabaseDocumentsByPrefix(
                database = eq("app"),
                prefix = eq("NotificationConfig"),
                kClass = eq(ObjectNode::class)
            )
        ).thenThrow(RuntimeException("couchdb unreachable"))
            .thenReturn(listOf(configDoc("user-1")))

        // when / then
        assertThatThrownBy { cache.findAll() }.hasMessage("couchdb unreachable")
        assertThat(cache.findAll().map { it.userIdentifier }).containsExactly("user-1")
    }

    private fun configDoc(userIdentifier: String): ObjectNode =
        objectMapper
            .createObjectNode()
            .put("_id", "NotificationConfig:$userIdentifier")
            .put("_rev", "1-abc")
            .also { doc -> doc.putArray("notificationRules") }
}
