package com.aamdigital.aambackendservice.notification.core.config

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import com.aamdigital.aambackendservice.notification.repository.NotificationConfigEntity
import com.aamdigital.aambackendservice.notification.repository.NotificationConfigRepository
import com.fasterxml.jackson.databind.ObjectMapper
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
import org.mockito.kotlin.whenever
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class CouchDbSyncNotificationConfigUseCaseTest {
    private lateinit var service: CouchDbSyncNotificationConfigUseCase

    @Mock
    lateinit var couchDbClient: CouchDbClient

    @Mock
    lateinit var notificationConfigRepository: NotificationConfigRepository

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        reset(couchDbClient, notificationConfigRepository)

        service =
            CouchDbSyncNotificationConfigUseCase(
                couchDbClient = couchDbClient,
                notificationConfigRepository = notificationConfigRepository
            )
    }

    @Test
    fun `should import config and expand or conditions into multiple notification rules`() {
        // given
        val conditionTree =
            objectMapper.readTree(
                """
                {
                                    "${'$'}or": [
                                        {"name": {"${'$'}eq": "Bert"}},
                                        {"age": {"${'$'}gte": "18"}}
                  ]
                }
                """.trimIndent()
            )
        val notificationConfigDto =
            NotificationConfigDto(
                id = "notifications:user-1",
                rev = "1-abc",
                channels = NotificationChannelConfig(push = true, email = false),
                notificationRules =
                    listOf(
                        NotificationRuleDto(
                            label = "Rule 1",
                            notificationType = NotificationType.ENTITY_CHANGE,
                            entityType = "Child",
                            changeType = listOf("updated"),
                            conditions = conditionTree,
                            enabled = true
                        )
                    )
            )

        whenever(
            couchDbClient.getDatabaseDocument(
                database = eq("config-db"),
                documentId = eq("notifications:user-1"),
                queryParams = any(),
                kClass = eq(NotificationConfigDto::class)
            )
        ).thenReturn(notificationConfigDto)
        whenever(notificationConfigRepository.findByUserIdentifier("user-1")).thenReturn(Optional.empty())
        whenever(notificationConfigRepository.save(any<NotificationConfigEntity>())).thenAnswer { it.arguments.first() }

        // when
        val result =
            service.run(
                SyncNotificationConfigRequest(
                    notificationConfigDatabase = "config-db",
                    notificationConfigId = "notifications:user-1",
                    notificationConfigRev = "1-abc"
                )
            )

        // then
        assertThat(result).isInstanceOf(UseCaseOutcome.Success::class.java)

        val configCaptor = argumentCaptor<NotificationConfigEntity>()
        org.mockito.kotlin.verify(notificationConfigRepository).save(configCaptor.capture())

        val importedConfig = configCaptor.firstValue
        assertThat(importedConfig.notificationRules).hasSize(2)
        assertThat(importedConfig.notificationRules.map { it.externalIdentifier })
            .doesNotContainNull()
            .doesNotHaveDuplicates()
        assertThat(importedConfig.notificationRules.map { it.conditions }).allSatisfy { conditions ->
            assertThat(conditions).hasSize(1)
        }
        assertThat(importedConfig.notificationRules.flatMap { it.conditions.map { c -> c.field } })
            .containsExactlyInAnyOrder("name", "age")

        val firstRunExternalIdentifiers = importedConfig.notificationRules.map { it.externalIdentifier }.sorted()

        // when running import with same payload again
        val secondResult =
            service.run(
                SyncNotificationConfigRequest(
                    notificationConfigDatabase = "config-db",
                    notificationConfigId = "notifications:user-1",
                    notificationConfigRev = "1-abc"
                )
            )

        // then external identifiers remain stable
        assertThat(secondResult).isInstanceOf(UseCaseOutcome.Success::class.java)
        val secondConfigCaptor = argumentCaptor<NotificationConfigEntity>()
        org.mockito.kotlin.verify(notificationConfigRepository, org.mockito.kotlin.times(2)).save(secondConfigCaptor.capture())
        val secondRunExternalIdentifiers = secondConfigCaptor.allValues.last().notificationRules.map { it.externalIdentifier }.sorted()
        assertThat(secondRunExternalIdentifiers).containsExactlyElementsOf(firstRunExternalIdentifiers)
    }
}
