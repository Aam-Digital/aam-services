package com.aamdigital.aambackendservice.notification.core.config

import com.aamdigital.aambackendservice.common.condition.DocumentConditionEngine
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.DefaultCouchDbClient.DefaultCouchDbClientErrorCode
import com.aamdigital.aambackendservice.common.couchdb.core.getEmptyQueryParams
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.common.placeholder.core.PlaceholderValueLookupException
import com.aamdigital.aambackendservice.common.placeholder.core.PlaceholderValueProvider
import com.aamdigital.aambackendservice.common.placeholder.core.Placeholders
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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@ExtendWith(MockitoExtension::class)
class DefaultNotificationConfigCacheTest {
    @Mock
    lateinit var couchDbClient: CouchDbClient

    private val objectMapper = jacksonObjectMapper()
    private val placeholderValueProvider = FakePlaceholderValueProvider()
    private lateinit var cache: DefaultNotificationConfigCache

    /** Placeholder values as Keycloak would have them, recording which lookups were made. */
    private class FakePlaceholderValueProvider : PlaceholderValueProvider {
        var values: Map<String, Any> = emptyMap()
        var failure: RuntimeException? = null
        val lookups = mutableListOf<Pair<String, Set<String>>>()

        override fun getValues(
            userIdentifier: String,
            placeholders: Set<String>
        ): Map<String, Any> {
            lookups.add(userIdentifier to placeholders)
            failure?.let { throw it }
            return values.filterKeys { it in placeholders }
        }
    }

    /** Clock the test advances by hand, so no test depends on wall-clock timing. */
    private class MutableClock(
        var now: Instant
    ) : Clock() {
        override fun instant(): Instant = now

        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId): Clock = this
    }

    @BeforeEach
    fun setUp() {
        cache =
            DefaultNotificationConfigCache(
                couchDbClient = couchDbClient,
                objectMapper = objectMapper,
                placeholderValueProvider = placeholderValueProvider
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

    @Test
    fun `should resolve the current user placeholder when loading the config`() {
        // given
        placeholderValueProvider.values = mapOf(Placeholders.CURRENT_USER to "User:alice")
        givenConfigDocs(configDocWithConditions("""{"assignedTo": {"${'$'}elemMatch": "${'$'}current_user"}}"""))

        // when
        val rule =
            cache
                .findAll()
                .single()
                .rules
                .single()

        // then
        val engine = DocumentConditionEngine()
        assertThat(engine.matches(rule.conditions, mapOf("assignedTo" to listOf("User:alice")))).isTrue
        assertThat(engine.matches(rule.conditions, mapOf("assignedTo" to listOf("User:bob")))).isFalse
        assertThat(placeholderValueProvider.lookups).containsExactly("user-1" to setOf(Placeholders.CURRENT_USER))
    }

    @Test
    fun `should derive the same rule identifier whether the placeholder was resolved by the frontend or here`() {
        // given
        placeholderValueProvider.values = mapOf(Placeholders.CURRENT_USER to "User:alice")
        givenConfigDocs(
            configDocWithConditions("""{"assignedTo": "${'$'}current_user"}"""),
            configDocWithConditions("""{"assignedTo": "User:alice"}""")
        )
        val otherCache =
            DefaultNotificationConfigCache(
                couchDbClient = couchDbClient,
                objectMapper = objectMapper,
                placeholderValueProvider = placeholderValueProvider
            )

        // when
        val withPlaceholder = cache.singleRule().externalIdentifier
        val resolvedByFrontend = otherCache.singleRule().externalIdentifier

        // then
        assertThat(withPlaceholder).isEqualTo(resolvedByFrontend)
    }

    @Test
    fun `should skip only the rule whose placeholder has no value for the user`() {
        // given no entity is linked to the user account
        givenConfigDocs(
            configDocWithRules(
                rule("Rule about me", """{"assignedTo": "${'$'}current_user"}"""),
                rule("Other rule", """{"name": "Bert"}""")
            )
        )

        // when
        val entries = cache.findAll()

        // then
        assertThat(entries.single().rules.map { it.label }).containsExactly("Other rule")
    }

    @Test
    fun `should not look up placeholder values for a config without placeholders`() {
        // given
        givenConfigDocs(configDocWithConditions("""{"name": "Bert"}"""))

        // when
        cache.findAll()

        // then
        assertThat(placeholderValueProvider.lookups).isEmpty()
    }

    @Test
    fun `should fail the load when placeholder values cannot be looked up for now, and load again next time`() {
        // given dropping the config would silently disable the user's rules until the next reload
        placeholderValueProvider.failure = PlaceholderValueLookupException("keycloak unreachable")
        givenConfigDocs(configDocWithConditions("""{"assignedTo": "${'$'}current_user"}"""))

        // when / then
        assertThatThrownBy { cache.findAll() }.isInstanceOf(PlaceholderValueLookupException::class.java)

        placeholderValueProvider.failure = null
        placeholderValueProvider.values = mapOf(Placeholders.CURRENT_USER to "User:alice")
        assertThat(cache.findAll().single().rules).hasSize(1)
    }

    @Test
    fun `should keep the cached config when placeholder values cannot be looked up during refresh`() {
        // given
        placeholderValueProvider.values = mapOf(Placeholders.CURRENT_USER to "User:alice")
        givenConfigDocs(configDocWithConditions("""{"assignedTo": "${'$'}current_user"}"""))
        val cached = cache.findAll()

        whenever(
            couchDbClient.getDatabaseDocument(
                database = eq("app"),
                documentId = eq("NotificationConfig:user-1"),
                queryParams = any(),
                kClass = eq(NotificationConfigDto::class)
            )
        ).thenReturn(
            objectMapper.convertValue(
                configDocWithConditions("""{"authors": "${'$'}current_user"}"""),
                NotificationConfigDto::class.java
            )
        )
        placeholderValueProvider.failure = PlaceholderValueLookupException("keycloak unreachable")

        // when
        cache.refreshConfig(database = "app", notificationConfigId = "NotificationConfig:user-1", deleted = false)

        // then
        assertThat(cache.findAll()).isEqualTo(cached)
    }

    @Test
    fun `should load again after the ttl, resolving placeholders with their current values`() {
        // given
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val cacheWithTtl =
            DefaultNotificationConfigCache(
                couchDbClient = couchDbClient,
                objectMapper = objectMapper,
                placeholderValueProvider = placeholderValueProvider,
                ttl = Duration.ofDays(1),
                clock = clock
            )
        placeholderValueProvider.values = mapOf(Placeholders.CURRENT_USER to "User:alice")
        givenConfigDocs(configDocWithConditions("""{"assignedTo": "${'$'}current_user"}"""))
        val engine = DocumentConditionEngine()
        val bobsDocument = mapOf("assignedTo" to "User:bob")

        val before = cacheWithTtl.singleRule()
        assertThat(engine.matches(before.conditions, bobsDocument)).isFalse

        // when the account is linked to a different entity, and the ttl passes
        placeholderValueProvider.values = mapOf(Placeholders.CURRENT_USER to "User:bob")
        clock.now = clock.now.plus(Duration.ofHours(23))
        val withinTtl = cacheWithTtl.singleRule()
        clock.now = clock.now.plus(Duration.ofHours(1))
        val afterTtl = cacheWithTtl.singleRule()

        // then
        assertThat(engine.matches(withinTtl.conditions, bobsDocument)).isFalse
        assertThat(engine.matches(afterTtl.conditions, bobsDocument)).isTrue
        verify(couchDbClient, times(2)).getDatabaseDocumentsByPrefix(any(), any(), eq(ObjectNode::class))
    }

    @Test
    fun `should keep using the previous configs when a reload after the ttl fails`() {
        // given
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val cacheWithTtl =
            DefaultNotificationConfigCache(
                couchDbClient = couchDbClient,
                objectMapper = objectMapper,
                placeholderValueProvider = placeholderValueProvider,
                ttl = Duration.ofDays(1),
                clock = clock
            )
        placeholderValueProvider.values = mapOf(Placeholders.CURRENT_USER to "User:alice")
        givenConfigDocs(configDocWithConditions("""{"assignedTo": "${'$'}current_user"}"""))
        val loaded = cacheWithTtl.findAll()

        // when the cache expires while Keycloak is unreachable
        placeholderValueProvider.failure = PlaceholderValueLookupException("keycloak unreachable")
        clock.now = clock.now.plus(Duration.ofDays(1))

        // then notifications keep working with the previous configs
        assertThat(cacheWithTtl.findAll()).isEqualTo(loaded)

        // and the reload is tried again later
        placeholderValueProvider.failure = null
        placeholderValueProvider.values = mapOf(Placeholders.CURRENT_USER to "User:bob")
        clock.now = clock.now.plus(DefaultNotificationConfigCache.RELOAD_RETRY_INTERVAL)
        val reloaded = cacheWithTtl.singleRule()
        assertThat(DocumentConditionEngine().matches(reloaded.conditions, mapOf("assignedTo" to "User:bob"))).isTrue
    }

    @Test
    fun `should leave out disabled rules without looking up their placeholders`() {
        // given
        givenConfigDocs(
            configDocWithRules(
                rule("Disabled rule about me", """{"assignedTo": "${'$'}current_user"}""", enabled = false),
                rule("Enabled rule", """{"name": "Bert"}""")
            )
        )

        // when
        val entries = cache.findAll()

        // then
        assertThat(entries.single().rules.map { it.label }).containsExactly("Enabled rule")
        assertThat(placeholderValueProvider.lookups).isEmpty()
    }

    private fun DefaultNotificationConfigCache.singleRule(): NotificationRuleCacheEntry =
        findAll()
            .single()
            .rules
            .single()

    private fun givenConfigDocs(vararg docsPerLoad: ObjectNode) {
        val loads = docsPerLoad.map { listOf(it) }
        whenever(
            couchDbClient.getDatabaseDocumentsByPrefix(
                database = eq("app"),
                prefix = eq("NotificationConfig"),
                kClass = eq(ObjectNode::class)
            )
        ).thenReturn(loads.first(), *loads.drop(1).toTypedArray())
    }

    private fun rule(
        label: String,
        conditions: String,
        enabled: Boolean = true
    ): String =
        """
        {
            "label": "$label",
            "notificationType": "entity_change",
            "entityType": "Todo",
            "changeType": ["created"],
            "conditions": $conditions,
            "enabled": $enabled
        }
        """.trimIndent()

    private fun configDocWithRules(vararg rules: String): ObjectNode =
        objectMapper
            .readTree(
                """
                {
                    "_id": "NotificationConfig:user-1",
                    "_rev": "1-abc",
                    "notificationRules": [${rules.joinToString(",")}]
                }
                """.trimIndent()
            ).deepCopy<ObjectNode>()

    private fun configDocWithConditions(conditions: String): ObjectNode = configDocWithRules(rule("Rule", conditions))

    private fun configDoc(userIdentifier: String): ObjectNode =
        objectMapper
            .createObjectNode()
            .put("_id", "NotificationConfig:$userIdentifier")
            .put("_rev", "1-abc")
            .also { doc -> doc.putArray("notificationRules") }
}
