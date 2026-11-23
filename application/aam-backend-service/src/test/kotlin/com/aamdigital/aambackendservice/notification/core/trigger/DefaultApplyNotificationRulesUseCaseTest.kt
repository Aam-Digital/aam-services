package com.aamdigital.aambackendservice.notification.core.trigger

import com.aamdigital.aambackendservice.common.changes.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.condition.DocumentConditionEngine
import com.aamdigital.aambackendservice.common.domain.ApplicationConfig
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.permission.core.PermissionCheckClient
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.core.config.NotificationConfigCache
import com.aamdigital.aambackendservice.notification.core.config.NotificationConfigCacheEntry
import com.aamdigital.aambackendservice.notification.core.config.NotificationRuleCacheEntry
import com.aamdigital.aambackendservice.notification.core.outbox.UserNotificationPublisher
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class DefaultApplyNotificationRulesUseCaseTest {
    private lateinit var service: DefaultApplyNotificationRulesUseCase

    @Mock
    lateinit var notificationConfigCache: NotificationConfigCache

    @Mock
    lateinit var userNotificationPublisher: UserNotificationPublisher

    @Mock
    lateinit var permissionCheckClient: PermissionCheckClient

    @BeforeEach
    fun setUp() {
        reset(
            notificationConfigCache,
            userNotificationPublisher,
            permissionCheckClient
        )

        service =
            DefaultApplyNotificationRulesUseCase(
                notificationConfigCaches = listOf(notificationConfigCache),
                userNotificationPublisher = userNotificationPublisher,
                permissionCheckClient = permissionCheckClient,
                applicationConfig = ApplicationConfig(baseUrl = "https://app.test"),
                emailEnabled = true,
                pushEnabled = true
            )

        Mockito.lenient().`when`(permissionCheckClient.checkPermissions(any(), any(), any())).thenReturn(
            mapOf("user-1" to true)
        )
    }

    private val documentUpdateEvent =
        DocumentChangeEvent(
            database = "app",
            documentId = "Child:14b69515-1d64-449a-802e-6eee0591021b",
            rev = "2-e5d65d1843b97422aaaa29ad3bfba900",
            currentVersion =
                mapOf(
                    "_id" to "Child:1",
                    "_rev" to "2-e5d65d1843b97422aaaa29ad3bfba900",
                    "created" to mapOf("at" to "2025-03-14T15:37:09.855Z"),
                    "updated" to mapOf("at" to "2025-03-14T15:37:09.855Z"),
                    "name" to "Bert",
                    "age" to 18,
                    "categories" to listOf("X", "Y")
                ),
            previousVersion =
                mapOf(
                    "_id" to "Child:1",
                    "_rev" to "1-a8358a730180c52af9b6973acd334e67",
                    "created" to mapOf("at" to "2025-03-14T15:37:09.855Z"),
                    "updated" to mapOf("at" to "2025-03-14T15:37:09.855Z"),
                    "age" to 17,
                    "name" to "Adam"
                ),
            deleted = false
        )

    private val documentCreateEvent =
        DocumentChangeEvent(
            database = "app",
            documentId = "Child:1",
            rev = "1-aa4598f080dc7db187cc3f59c92fd7fd",
            currentVersion =
                mapOf(
                    "_id" to "Child:1",
                    "_rev" to "1-aa4598f080dc7db187cc3f59c92fd7fd",
                    "name" to "New Doc"
                ),
            previousVersion = emptyMap<String, Any>(),
            deleted = false
        )

    private val documentDeleteEvent =
        DocumentChangeEvent(
            database = "app",
            documentId = "Child:1",
            rev = "3-dc4f892af6233d08f67f8817d2e28bb2",
            currentVersion = emptyMap<String, Any>(),
            previousVersion = emptyMap<String, Any>(),
            deleted = true
        )

    private val documentOtherUpdateEvent =
        DocumentChangeEvent(
            database = "app",
            documentId = "School:1",
            rev = "2-e5d65d1843b97422aaaa29ad3bfba900",
            currentVersion =
                mapOf(
                    "_id" to "School:1",
                    "_rev" to "2-e5d65d1843b97422aaaa29ad3bfba900",
                    "created" to mapOf("at" to "2025-03-14T15:37:09.855Z"),
                    "updated" to mapOf("at" to "2025-03-14T15:37:09.855Z"),
                    "name" to "Other School +"
                ),
            previousVersion =
                mapOf(
                    "_id" to "School:1",
                    "_rev" to "1-a8358a730180c52af9b6973acd334e67",
                    "created" to mapOf("at" to "2025-03-14T15:37:09.855Z"),
                    "updated" to mapOf("at" to "2025-03-14T15:37:09.855Z"),
                    "name" to "Other School"
                ),
            deleted = false
        )

    // Simulates an update where the previous revision was purged (empty previousVersion)
    private val documentUpdateWithPurgedPreviousEvent =
        DocumentChangeEvent(
            database = "app",
            documentId = "Child:14b69515-1d64-449a-802e-6eee0591021b",
            rev = "2-e5d65d1843b97422aaaa29ad3bfba900",
            currentVersion =
                mapOf(
                    "_id" to "Child:1",
                    "_rev" to "2-e5d65d1843b97422aaaa29ad3bfba900",
                    "name" to "Updated Doc"
                ),
            previousVersion = emptyMap<String, Any>(),
            deleted = false
        )

    private val documentChangeEvents =
        mapOf(
            "created" to documentCreateEvent,
            "updated" to documentUpdateEvent,
            "deleted" to documentDeleteEvent,
            "other" to documentOtherUpdateEvent,
            "updatedPurged" to documentUpdateWithPurgedPreviousEvent
        )

    private val documentConditionEngine = DocumentConditionEngine()
    private val objectMapper = ObjectMapper()

    private fun conditions(json: String = "{}") = documentConditionEngine.parse(objectMapper.readTree(json))

    private fun generateNotificationConfig(
        changeType: String,
        conditions: String = "{}",
        entityType: String = "Child",
        enabled: Boolean = true
    ): NotificationConfigCacheEntry =
        NotificationConfigCacheEntry(
            channelPush = true,
            channelEmail = true,
            userIdentifier = "user-1",
            rules =
                listOf(
                    NotificationRuleCacheEntry(
                        label = "Notification Rule 1",
                        externalIdentifier = "external-identifier-1",
                        notificationType = NotificationType.ENTITY_CHANGE,
                        entityType = entityType,
                        changeType = changeType,
                        conditions = conditions(conditions),
                        enabled = enabled
                    )
                )
        )

    @ParameterizedTest
    @CsvSource(
        "created,1",
        "updated,0",
        "deleted,0",
        "other,0"
    )
    fun `should trigger notification when child is created`(
        documentEventType: String,
        notificationsSendCount: Int
    ) {
        // given
        whenever(notificationConfigCache.findAll()).thenReturn(
            listOf(
                generateNotificationConfig(changeType = "created")
            )
        )

        // when
        val result =
            service.run(
                ApplyNotificationRulesRequest(
                    documentChangeEvent = documentChangeEvents[documentEventType]!!
                )
            )

        // then
        assertThat(result).isInstanceOf(UseCaseOutcome.Success::class.java)
        assertEquals(notificationsSendCount, (result as UseCaseOutcome.Success).data.notificationsSendCount)
    }

    @ParameterizedTest
    @CsvSource(
        "created,0",
        "updated,1",
        "deleted,0",
        "other,0"
    )
    fun `should trigger notification when child is updated`(
        documentEventType: String,
        notificationsSendCount: Int
    ) {
        // given
        whenever(notificationConfigCache.findAll()).thenReturn(
            listOf(
                generateNotificationConfig(
                    changeType = "updated"
                ),
                generateNotificationConfig(
                    changeType = "created",
                    enabled = false
                )
            )
        )

        // when
        val result =
            service.run(
                ApplyNotificationRulesRequest(
                    documentChangeEvent =
                        when (documentEventType) {
                            "created" -> documentCreateEvent
                            "updated" -> documentUpdateEvent
                            "deleted" -> documentDeleteEvent
                            "other" -> documentOtherUpdateEvent
                            else -> throw IllegalArgumentException("Invalid document event type")
                        }
                )
            )

        // then
        assertThat(result).isInstanceOf(UseCaseOutcome.Success::class.java)
        assertEquals(notificationsSendCount, (result as UseCaseOutcome.Success).data.notificationsSendCount)
    }

    @ParameterizedTest
    @CsvSource(
        "created,0",
        "updated,0",
        "deleted,1",
        "other,0"
    )
    fun `should trigger notification when child is deleted`(
        documentEventType: String,
        notificationsSendCount: Int
    ) {
        // given
        whenever(notificationConfigCache.findAll()).thenReturn(
            listOf(
                generateNotificationConfig(changeType = "deleted")
            )
        )

        // when
        val result =
            service.run(
                ApplyNotificationRulesRequest(
                    documentChangeEvent = documentChangeEvents[documentEventType]!!
                )
            )

        // then
        assertThat(result).isInstanceOf(UseCaseOutcome.Success::class.java)
        assertEquals(notificationsSendCount, (result as UseCaseOutcome.Success).data.notificationsSendCount)
    }

    @ParameterizedTest
    @CsvSource(
        "created,0",
        "updated,8",
        "deleted,0",
        "other,0"
    )
    fun `should trigger notification when child is updated with matching condition`(
        documentEventType: String,
        notificationsSendCount: Int
    ) {
        // given
        whenever(notificationConfigCache.findAll()).thenReturn(
            listOf(
                // condition $eq matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = "{\"name\": {\"\$eq\": \"Bert\"}}"
                ),
                // condition $eq not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = "{\"name\": {\"\$eq\": \"Clark\"}}"
                ),
                // condition $ne matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = "{\"name\": {\"\$ne\": \"XYZ\"}}"
                ),
                // condition $ne not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = "{\"name\": {\"\$ne\": \"Bert\"}}"
                ),
                // condition $elemMatch matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = "{\"categories\": {\"\$elemMatch\": {\"\$in\": [\"X\"]}}}"
                ),
                // condition $elemMatch not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = "{\"categories\": {\"\$elemMatch\": {\"\$in\": [\"ABC\"]}}}"
                ),
                // condition $gt matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = "{\"age\": {\"\$gt\": 17}}"
                ),
                // condition $gt not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = "{\"age\": {\"\$gt\": 18}}"
                ),
                // condition $gte matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = "{\"age\": {\"\$gte\": 18}}"
                ),
                // condition $gte not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = "{\"age\": {\"\$gte\": 19}}"
                ),
                // condition $lt matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = "{\"age\": {\"\$lt\": 19}}"
                ),
                // condition $lt not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = "{\"age\": {\"\$lt\": 18}}"
                ),
                // condition $lte matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = "{\"age\": {\"\$lte\": 18}}"
                ),
                // condition $lte not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = "{\"age\": {\"\$lte\": 17}}"
                ),
                // condition $not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = "{\"name\": {\"\$not\": {\"\$eq\": \"Clark\"}}}"
                ),
                // condition $not not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = "{\"name\": {\"\$not\": {\"\$eq\": \"Bert\"}}}"
                )
            )
        )

        // when
        val result =
            service.run(
                ApplyNotificationRulesRequest(
                    documentChangeEvent = documentChangeEvents[documentEventType]!!
                )
            )

        // then
        assertThat(result).isInstanceOf(UseCaseOutcome.Success::class.java)
        assertEquals(notificationsSendCount, (result as UseCaseOutcome.Success).data.notificationsSendCount)
    }

    @Test
    fun `should skip a rule whose conditions turn out to be invalid and still apply the others`() {
        // given the unknown operator is only reached for a document named "Bert"
        whenever(notificationConfigCache.findAll()).thenReturn(
            listOf(
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = "{\"\$and\": [{\"name\": \"Bert\"}, {\"age\": {\"\$unknown\": 1}}]}"
                ),
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = "{\"name\": \"Bert\"}"
                )
            )
        )

        // when
        val result =
            service.run(
                ApplyNotificationRulesRequest(documentChangeEvent = documentUpdateEvent)
            )

        // then
        assertThat(result).isInstanceOf(UseCaseOutcome.Success::class.java)
        assertEquals(1, (result as UseCaseOutcome.Success).data.notificationsSendCount)
    }

    @ParameterizedTest
    @CsvSource(
        "created,created,1",
        "created,updatedPurged,0",
        "updated,updatedPurged,1"
    )
    fun `should correctly classify update with purged previous version`(
        ruleChangeType: String,
        documentEventType: String,
        notificationsSendCount: Int
    ) {
        // given
        whenever(notificationConfigCache.findAll()).thenReturn(
            listOf(
                generateNotificationConfig(changeType = ruleChangeType)
            )
        )

        // when
        val result =
            service.run(
                ApplyNotificationRulesRequest(
                    documentChangeEvent = documentChangeEvents[documentEventType]!!
                )
            )

        // then
        assertThat(result).isInstanceOf(UseCaseOutcome.Success::class.java)
        assertEquals(notificationsSendCount, (result as UseCaseOutcome.Success).data.notificationsSendCount)
    }

    private fun singleRuleConfig() =
        listOf(
            NotificationConfigCacheEntry(
                channelPush = false,
                channelEmail = false,
                userIdentifier = "user-1",
                rules =
                    listOf(
                        NotificationRuleCacheEntry(
                            label = "Rule 1",
                            externalIdentifier = "ext-1",
                            notificationType = NotificationType.ENTITY_CHANGE,
                            entityType = "Child",
                            changeType = "created",
                            conditions = conditions(),
                            enabled = true
                        )
                    )
            )
        )

    @Test
    fun `should not publish PUSH notification when no push handler is configured`() {
        // Given a channel with no handler produces notifications that can never be delivered
        val serviceWithoutPush =
            DefaultApplyNotificationRulesUseCase(
                notificationConfigCaches = listOf(notificationConfigCache),
                userNotificationPublisher = userNotificationPublisher,
                permissionCheckClient = permissionCheckClient,
                applicationConfig = ApplicationConfig(baseUrl = "https://app.test"),
                emailEnabled = false,
                pushEnabled = false
            )
        whenever(notificationConfigCache.findAll()).thenReturn(singleRuleConfig())

        // When
        serviceWithoutPush.run(ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent))

        // Then
        val eventCaptor = argumentCaptor<CreateUserNotificationEvent>()
        verify(userNotificationPublisher, times(1)).publish(
            eventCaptor.capture()
        )
        assertThat(eventCaptor.allValues.map { it.notificationChannelType })
            .containsExactly(NotificationChannelType.APP)
    }

    @Test
    fun `should derive the same notification id when the same change is processed twice`() {
        // Given reprocessing a change must not deliver the notification a second time, so the id
        // is derived from the change rather than generated fresh
        whenever(notificationConfigCache.findAll()).thenReturn(singleRuleConfig())

        // When
        service.run(ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent))
        service.run(ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent))

        // Then
        val eventCaptor = argumentCaptor<CreateUserNotificationEvent>()
        verify(userNotificationPublisher, times(4)).publish(
            eventCaptor.capture()
        )
        assertThat(eventCaptor.allValues.map { it.details.id }.distinct()).hasSize(1)
    }

    @Test
    fun `should derive a different notification id for a different document revision`() {
        // Given
        whenever(notificationConfigCache.findAll()).thenReturn(singleRuleConfig())

        // When
        service.run(ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent))
        service.run(
            ApplyNotificationRulesRequest(
                documentChangeEvent = documentCreateEvent.copy(rev = "1-different")
            )
        )

        // Then
        val eventCaptor = argumentCaptor<CreateUserNotificationEvent>()
        verify(userNotificationPublisher, times(4)).publish(
            eventCaptor.capture()
        )
        assertThat(eventCaptor.allValues.map { it.details.id }.distinct()).hasSize(2)
    }

    @Test
    fun `should always publish APP notification for every matched rule`() {
        // given
        whenever(notificationConfigCache.findAll()).thenReturn(
            listOf(
                NotificationConfigCacheEntry(
                    channelPush = false,
                    channelEmail = false,
                    userIdentifier = "user-1",
                    rules =
                        listOf(
                            NotificationRuleCacheEntry(
                                label = "Rule 1",
                                externalIdentifier = "ext-1",
                                notificationType = NotificationType.ENTITY_CHANGE,
                                entityType = "Child",
                                changeType = "created",
                                conditions = conditions(),
                                enabled = true
                            )
                        )
                )
            )
        )

        // when
        val result =
            service.run(
                ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent)
            )

        // then
        assertThat(result).isInstanceOf(UseCaseOutcome.Success::class.java)

        val eventCaptor = argumentCaptor<CreateUserNotificationEvent>()
        verify(userNotificationPublisher, times(2)).publish(
            eventCaptor.capture()
        )
        assertThat(eventCaptor.allValues.map { it.notificationChannelType })
            .containsExactlyInAnyOrder(
                NotificationChannelType.APP,
                // currently always sent regardless of the channelPush config flag, see TODO in use case
                NotificationChannelType.PUSH
            )
    }

    @Test
    fun `should always publish PUSH notification regardless of channelPush flag`() {
        // Note: push is currently always sent regardless of the channelPush config flag.
        // This is because we currently only have device-level registration for push notifications, not globally for all devices of one user.
        // TODO: replace with per-device registration check (see TODO in use case).

        // given
        whenever(notificationConfigCache.findAll()).thenReturn(
            listOf(
                NotificationConfigCacheEntry(
                    channelPush = false,
                    channelEmail = false,
                    userIdentifier = "user-1",
                    rules =
                        listOf(
                            NotificationRuleCacheEntry(
                                label = "Rule 1",
                                externalIdentifier = "ext-1",
                                notificationType = NotificationType.ENTITY_CHANGE,
                                entityType = "Child",
                                changeType = "created",
                                conditions = conditions(),
                                enabled = true
                            )
                        )
                )
            )
        )

        // when
        val result =
            service.run(
                ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent)
            )

        // then
        assertThat(result).isInstanceOf(UseCaseOutcome.Success::class.java)

        val eventCaptor = argumentCaptor<CreateUserNotificationEvent>()
        verify(userNotificationPublisher, times(2)).publish(
            eventCaptor.capture()
        )
        val channelTypes = eventCaptor.allValues.map { it.notificationChannelType }
        assertThat(channelTypes).containsExactlyInAnyOrder(
            NotificationChannelType.APP,
            NotificationChannelType.PUSH
        )
    }

    @Test
    fun `should also publish EMAIL notification when channelEmail flag is enabled`() {
        // given
        whenever(notificationConfigCache.findAll()).thenReturn(
            listOf(
                NotificationConfigCacheEntry(
                    channelPush = false,
                    channelEmail = true,
                    userIdentifier = "user-1",
                    rules =
                        listOf(
                            NotificationRuleCacheEntry(
                                label = "Rule 1",
                                externalIdentifier = "ext-1",
                                notificationType = NotificationType.ENTITY_CHANGE,
                                entityType = "Child",
                                changeType = "created",
                                conditions = conditions(),
                                enabled = true
                            )
                        )
                )
            )
        )

        // when
        val result =
            service.run(
                ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent)
            )

        // then
        assertThat(result).isInstanceOf(UseCaseOutcome.Success::class.java)

        val eventCaptor = argumentCaptor<CreateUserNotificationEvent>()
        verify(userNotificationPublisher, times(3)).publish(
            eventCaptor.capture()
        )
        val channelTypes = eventCaptor.allValues.map { it.notificationChannelType }
        assertThat(channelTypes).containsExactlyInAnyOrder(
            NotificationChannelType.APP,
            // currently always sent regardless of the channelPush config flag, see TODO in use case
            NotificationChannelType.PUSH,
            NotificationChannelType.EMAIL
        )
    }

    @Test
    fun `should not publish notification when permission check denies user access`() {
        // given
        whenever(notificationConfigCache.findAll()).thenReturn(
            listOf(
                generateNotificationConfig(changeType = "created")
            )
        )
        whenever(permissionCheckClient.checkPermissions(any(), any(), any())).thenReturn(
            mapOf("user-1" to false)
        )

        // when
        val result =
            service.run(
                ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent)
            )

        // then
        assertThat(result).isInstanceOf(UseCaseOutcome.Success::class.java)
        assertEquals(0, (result as UseCaseOutcome.Success).data.notificationsSendCount)
        verify(userNotificationPublisher, times(0)).publish(any())
    }

    @Test
    fun `should not publish notification when permission check fails`() {
        // given
        whenever(notificationConfigCache.findAll()).thenReturn(
            listOf(
                generateNotificationConfig(changeType = "created")
            )
        )
        whenever(permissionCheckClient.checkPermissions(any(), any(), any())).thenReturn(
            emptyMap()
        )

        // when
        val result =
            service.run(
                ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent)
            )

        // then
        assertThat(result).isInstanceOf(UseCaseOutcome.Success::class.java)
        assertEquals(0, (result as UseCaseOutcome.Success).data.notificationsSendCount)
        verify(userNotificationPublisher, times(0)).publish(any())
    }

    @Test
    fun `should not publish EMAIL notification when email feature is disabled`() {
        // given
        val serviceWithEmailDisabled =
            DefaultApplyNotificationRulesUseCase(
                notificationConfigCaches = listOf(notificationConfigCache),
                userNotificationPublisher = userNotificationPublisher,
                permissionCheckClient = permissionCheckClient,
                applicationConfig = ApplicationConfig(baseUrl = "https://app.test"),
                emailEnabled = false,
                pushEnabled = true
            )
        whenever(notificationConfigCache.findAll()).thenReturn(
            listOf(
                NotificationConfigCacheEntry(
                    channelPush = false,
                    channelEmail = true,
                    userIdentifier = "user-1",
                    rules =
                        listOf(
                            NotificationRuleCacheEntry(
                                label = "Rule 1",
                                externalIdentifier = "ext-1",
                                notificationType = NotificationType.ENTITY_CHANGE,
                                entityType = "Child",
                                changeType = "created",
                                conditions = conditions(),
                                enabled = true
                            )
                        )
                )
            )
        )

        // when
        val result =
            serviceWithEmailDisabled.run(
                ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent)
            )

        // then
        assertThat(result).isInstanceOf(UseCaseOutcome.Success::class.java)
        val eventCaptor = argumentCaptor<CreateUserNotificationEvent>()
        verify(userNotificationPublisher, times(2)).publish(
            eventCaptor.capture()
        )
        assertThat(eventCaptor.allValues.map { it.notificationChannelType })
            .doesNotContain(NotificationChannelType.EMAIL)
    }

    @Test
    fun `should populate actionUrl with base url and notification id`() {
        // given
        whenever(notificationConfigCache.findAll()).thenReturn(
            listOf(generateNotificationConfig(changeType = "created"))
        )

        // when
        service.run(ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent))

        // then
        val eventCaptor = argumentCaptor<CreateUserNotificationEvent>()
        verify(userNotificationPublisher, Mockito.atLeastOnce()).publish(
            eventCaptor.capture()
        )
        val firstEvent = eventCaptor.firstValue
        assertThat(firstEvent.details.actionUrl).startsWith("https://app.test/notification/")
        assertThat(firstEvent.details.actionUrl).doesNotContain("?")
    }

    @Test
    fun `should use same notification id across all channel events for the same rule match`() {
        // given
        whenever(notificationConfigCache.findAll()).thenReturn(
            listOf(
                NotificationConfigCacheEntry(
                    channelPush = false,
                    channelEmail = true,
                    userIdentifier = "user-1",
                    rules =
                        listOf(
                            NotificationRuleCacheEntry(
                                label = "Rule 1",
                                externalIdentifier = "ext-1",
                                notificationType = NotificationType.ENTITY_CHANGE,
                                entityType = "Child",
                                changeType = "created",
                                conditions = conditions(),
                                enabled = true
                            )
                        )
                )
            )
        )

        // when
        service.run(ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent))

        // then
        val eventCaptor = argumentCaptor<CreateUserNotificationEvent>()
        verify(userNotificationPublisher, times(3)).publish(
            eventCaptor.capture()
        )
        val ids = eventCaptor.allValues.map { it.details.id }.distinct()
        assertThat(ids).hasSize(1)
    }

    /** A public form's confirmation rule: the recipient is whoever submitted the record. */
    private fun publicFormConfig() =
        NotificationConfigCacheEntry(
            userIdentifier = "form-1",
            channelPush = false,
            channelEmail = true,
            recipientEmailField = "email",
            rules =
                listOf(
                    NotificationRuleCacheEntry(
                        label = "Submission received",
                        externalIdentifier = "form-1-rule",
                        notificationType = NotificationType.ENTITY_CHANGE,
                        entityType = "Child",
                        changeType = "created",
                        conditions = conditions("""{"created.by": "PublicForm:form-1"}"""),
                        enabled = true
                    )
                )
        )

    private fun publicFormSubmission(vararg fields: Pair<String, Any>) =
        DocumentChangeEvent(
            database = "app",
            documentId = "Child:1",
            rev = "1-aa4598f080dc7db187cc3f59c92fd7fd",
            currentVersion =
                mapOf("_id" to "Child:1", "created" to mapOf("by" to "PublicForm:form-1")) + fields.toMap(),
            previousVersion = emptyMap<String, Any>(),
            deleted = false
        )

    @Test
    fun `should still apply the rules it can read when another rule source is unavailable`() {
        // given a second rule source whose first load fails, e.g. CouchDB being briefly unreachable
        val failingCache = Mockito.mock(NotificationConfigCache::class.java)
        whenever(failingCache.findAll()).thenThrow(IllegalStateException("couchdb unreachable"))
        whenever(notificationConfigCache.findAll()).thenReturn(listOf(publicFormConfig()))
        service =
            DefaultApplyNotificationRulesUseCase(
                notificationConfigCaches = listOf(failingCache, notificationConfigCache),
                userNotificationPublisher = userNotificationPublisher,
                permissionCheckClient = permissionCheckClient,
                applicationConfig = ApplicationConfig(baseUrl = "https://app.test"),
                emailEnabled = true,
                pushEnabled = true
            )

        // when
        val outcome = service.run(ApplyNotificationRulesRequest(publicFormSubmission("email" to "a@b.test")))

        // then the readable source still produced its notification
        assertThat((outcome as UseCaseOutcome.Success).data.notificationsSendCount).isEqualTo(1)
        verify(userNotificationPublisher).publish(any())
    }

    @Test
    fun `should email the address on the submitted record, without permission check or in-app notification`() {
        // given
        whenever(notificationConfigCache.findAll()).thenReturn(listOf(publicFormConfig()))

        // when
        service.run(ApplyNotificationRulesRequest(publicFormSubmission("email" to "  participant@example.org ")))

        // then nobody is asked whether the submitter may read their own record
        verify(permissionCheckClient, never()).checkPermissions(any(), any(), any())

        val eventCaptor = argumentCaptor<CreateUserNotificationEvent>()
        verify(userNotificationPublisher).publish(eventCaptor.capture())
        val event = eventCaptor.firstValue
        assertThat(event.notificationChannelType).isEqualTo(NotificationChannelType.EMAIL)
        assertThat(event.recipientEmail).isEqualTo("participant@example.org")
        assertThat(event.userIdentifier).isEqualTo("form-1")
    }

    @Test
    fun `should send nothing when the submitted record has no address in the configured field`() {
        // given
        whenever(notificationConfigCache.findAll()).thenReturn(listOf(publicFormConfig()))

        // when
        val outcome = service.run(ApplyNotificationRulesRequest(publicFormSubmission("name" to "No email given")))

        // then no undeliverable entry reaches the outbox
        verify(userNotificationPublisher, never()).publish(any())
        assertThat((outcome as UseCaseOutcome.Success).data.notificationsSendCount).isZero()
    }

    private fun unlabeledRuleConfig(label: String) =
        singleRuleConfig().map { config ->
            config.copy(rules = config.rules.map { it.copy(label = label) })
        }

    @Test
    fun `should use the rule label as notification title`() {
        // given
        whenever(notificationConfigCache.findAll()).thenReturn(singleRuleConfig())

        // when
        service.run(ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent))

        // then
        val eventCaptor = argumentCaptor<CreateUserNotificationEvent>()
        verify(userNotificationPublisher, Mockito.atLeastOnce()).publish(eventCaptor.capture())
        assertThat(eventCaptor.allValues.map { it.details.title }.distinct()).containsExactly("Rule 1")
    }

    private fun serviceWithEntityTypeLabels(requestedEntityTypes: MutableList<String>) =
        DefaultApplyNotificationRulesUseCase(
            notificationConfigCaches = listOf(notificationConfigCache),
            userNotificationPublisher = userNotificationPublisher,
            permissionCheckClient = permissionCheckClient,
            applicationConfig = ApplicationConfig(baseUrl = "https://app.test"),
            emailEnabled = false,
            pushEnabled = false,
            entityTypeLabelProvider = { entityType ->
                requestedEntityTypes.add(entityType)
                "Participant"
            }
        )

    @ParameterizedTest
    @CsvSource(value = ["''", "' '"])
    fun `should generate a title from entity type label and change type for a rule without label`(label: String) {
        // given
        val requestedEntityTypes = mutableListOf<String>()
        whenever(notificationConfigCache.findAll()).thenReturn(unlabeledRuleConfig(label))

        // when
        serviceWithEntityTypeLabels(requestedEntityTypes)
            .run(ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent))

        // then
        val eventCaptor = argumentCaptor<CreateUserNotificationEvent>()
        verify(userNotificationPublisher).publish(eventCaptor.capture())
        assertThat(eventCaptor.firstValue.details.title).isEqualTo("Participant created")
        assertThat(requestedEntityTypes).containsExactly("Child")
    }

    @Test
    fun `should look up the entity type label once per change for several rules without label`() {
        // given two users with a rule without label each
        val requestedEntityTypes = mutableListOf<String>()
        val config = unlabeledRuleConfig("").single()
        whenever(notificationConfigCache.findAll()).thenReturn(listOf(config, config.copy(userIdentifier = "user-2")))
        whenever(permissionCheckClient.checkPermissions(any(), any(), any()))
            .thenReturn(mapOf("user-1" to true, "user-2" to true))

        // when
        serviceWithEntityTypeLabels(requestedEntityTypes)
            .run(ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent))

        // then
        val eventCaptor = argumentCaptor<CreateUserNotificationEvent>()
        verify(userNotificationPublisher, times(2)).publish(eventCaptor.capture())
        assertThat(eventCaptor.allValues.map { it.details.title }).containsOnly("Participant created")
        assertThat(requestedEntityTypes).containsExactly("Child")
    }

    @Test
    fun `should not look up the entity type label if every matched rule has a label`() {
        // given
        val requestedEntityTypes = mutableListOf<String>()
        whenever(notificationConfigCache.findAll()).thenReturn(singleRuleConfig())

        // when
        serviceWithEntityTypeLabels(requestedEntityTypes)
            .run(ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent))

        // then
        verify(userNotificationPublisher).publish(any())
        assertThat(requestedEntityTypes).isEmpty()
    }

    @Test
    fun `should fall back to the entity type id in the title if the entity type has no label`() {
        // given
        whenever(notificationConfigCache.findAll()).thenReturn(unlabeledRuleConfig(""))

        // when
        service.run(ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent))

        // then
        val eventCaptor = argumentCaptor<CreateUserNotificationEvent>()
        verify(userNotificationPublisher, Mockito.atLeastOnce()).publish(eventCaptor.capture())
        assertThat(eventCaptor.allValues.map { it.details.title }.distinct()).containsExactly("Child created")
    }
}
