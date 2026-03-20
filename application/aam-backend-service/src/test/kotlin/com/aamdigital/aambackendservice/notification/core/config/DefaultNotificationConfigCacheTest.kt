package com.aamdigital.aambackendservice.notification.core.config

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.getEmptyQueryParams
import com.aamdigital.aambackendservice.common.couchdb.core.getQueryParamsAllDocs
import com.aamdigital.aambackendservice.common.couchdb.core.DefaultCouchDbClient.DefaultCouchDbClientErrorCode
import com.aamdigital.aambackendservice.common.couchdb.dto.CouchDbChange
import com.aamdigital.aambackendservice.common.couchdb.dto.CouchDbRow
import com.aamdigital.aambackendservice.common.couchdb.dto.CouchDbSearchResponse
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
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
    fun `should load notification configs from couchdb and expand rule conditions`() {
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
                                                    "changeType": ["updated"],
                                                    "conditions": {
                                                        "${'$'}or": [
                                                            {"name": {"${'$'}eq": "Bert"}},
                                                            {"age": {"${'$'}gte": "18"}}
                                                        ]
                                                    },
                                                    "enabled": true
                                                }
                                            ]
                                        }
                                        """.trimIndent()
                                ).deepCopy<ObjectNode>()

        whenever(
            couchDbClient.getDatabaseDocument(
                database = eq("app"),
                documentId = eq("_all_docs"),
                queryParams = eq(getQueryParamsAllDocs("NotificationConfig")),
                kClass = eq(CouchDbSearchResponse::class)
            )
        ).thenReturn(
            CouchDbSearchResponse(
                totalRows = 1,
                offset = 0,
                rows =
                    listOf(
                        CouchDbRow(
                            id = "NotificationConfig:user-1",
                            key = "NotificationConfig:user-1",
                            value = CouchDbChange(rev = "1-abc"),
                            doc = configDoc
                        )
                    )
            )
        )

        // when
        cache.refreshAll()
        val entries = cache.findAll()

        // then
        assertThat(entries).hasSize(1)
        assertThat(entries.first().userIdentifier).isEqualTo("user-1")
        assertThat(entries.first().rules).hasSize(2)
        assertThat(entries.first().rules.map { it.externalIdentifier })
            .doesNotContainNull()
            .doesNotHaveDuplicates()
    }

    @Test
    fun `should refresh one notification config in cache`() {
        // given
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
        // given
        whenever(
            couchDbClient.getDatabaseDocument(
                database = any(),
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
            database = "app",
            notificationConfigId = "NotificationConfig:user-1",
            deleted = false
        )
        assertThat(cache.findAll()).hasSize(1)

        // when
        cache.refreshConfig(
            database = "app",
            notificationConfigId = "NotificationConfig:user-1",
            deleted = true
        )

        // then
        assertThat(cache.findAll()).isEmpty()
    }

    @Test
    fun `should remove notification config when refresh fetch returns not found`() {
        // given
        whenever(
            couchDbClient.getDatabaseDocument(
                database = any(),
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
            database = "app",
            notificationConfigId = "NotificationConfig:user-1",
            deleted = false
        )
        assertThat(cache.findAll()).hasSize(1)

        whenever(
            couchDbClient.getDatabaseDocument(
                database = any(),
                documentId = eq("NotificationConfig:user-1"),
                queryParams = any(),
                kClass = eq(NotificationConfigDto::class)
            )
        ).thenThrow(NotFoundException("not found", code = DefaultCouchDbClientErrorCode.NOT_FOUND))

        // when
        cache.refreshConfig(
            database = "app",
            notificationConfigId = "NotificationConfig:user-1",
            deleted = false
        )

        // then
        assertThat(cache.findAll()).isEmpty()
    }
}
