package com.aamdigital.aambackendservice.notification.core.trigger

import com.aamdigital.aambackendservice.common.changes.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.condition.DocumentCondition
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.permission.core.PermissionCheckClient
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.core.config.NotificationConfigCache
import com.aamdigital.aambackendservice.notification.di.NotificationQueueConfiguration.Companion.USER_NOTIFICATION_QUEUE
import com.aamdigital.aambackendservice.notification.core.config.NotificationConfigCacheEntry
import com.aamdigital.aambackendservice.notification.core.config.NotificationRuleCacheEntry
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import com.aamdigital.aambackendservice.notification.queue.UserNotificationPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
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
                notificationConfigCache = notificationConfigCache,
                userNotificationPublisher = userNotificationPublisher,
                permissionCheckClient = permissionCheckClient
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

    private fun generateNotificationConfig(
        changeType: String,
        conditions: List<DocumentCondition> = emptyList(),
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
                        conditions = conditions,
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
        "updated,7",
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
                    conditions =
                        listOf(
                            DocumentCondition(
                                field = "name",
                                operator = "\$eq",
                                value = "Bert"
                            )
                        )
                ),
                // condition $eq not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions =
                        listOf(
                            DocumentCondition(
                                field = "name",
                                operator = "\$eq",
                                value = "Clark"
                            )
                        )
                ),
                // condition $nq matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions =
                        listOf(
                            DocumentCondition(
                                field = "name",
                                operator = "\$nq",
                                value = "XYZ"
                            )
                        )
                ),
                // condition $nq not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions =
                        listOf(
                            DocumentCondition(
                                field = "name",
                                operator = "\$nq",
                                value = "Bert"
                            )
                        )
                ),
                // condition $elemMatch matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions =
                        listOf(
                            DocumentCondition(
                                field = "categories",
                                operator = "\$elemMatch",
                                value = "X"
                            )
                        )
                ),
                // condition $elemMatch not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions =
                        listOf(
                            DocumentCondition(
                                field = "categories",
                                operator = "\$elemMatch",
                                value = "ABC"
                            )
                        )
                ),
                // condition $gt matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions =
                        listOf(
                            DocumentCondition(
                                field = "age",
                                operator = "\$gt",
                                value = "19"
                            )
                        )
                ),
                // condition $gt not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions =
                        listOf(
                            DocumentCondition(
                                field = "age",
                                operator = "\$gt",
                                value = "18"
                            )
                        )
                ),
                // condition $gte matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions =
                        listOf(
                            DocumentCondition(
                                field = "age",
                                operator = "\$gte",
                                value = "18"
                            )
                        )
                ),
                // condition $gte not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions =
                        listOf(
                            DocumentCondition(
                                field = "age",
                                operator = "\$gte",
                                value = "17"
                            )
                        )
                ),
                // condition $lt matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions =
                        listOf(
                            DocumentCondition(
                                field = "age",
                                operator = "\$lt",
                                value = "17"
                            )
                        )
                ),
                // condition $lt not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions =
                        listOf(
                            DocumentCondition(
                                field = "age",
                                operator = "\$lt",
                                value = "18"
                            )
                        )
                ),
                // condition $lte matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions =
                        listOf(
                            DocumentCondition(
                                field = "age",
                                operator = "\$lte",
                                value = "18"
                            )
                        )
                ),
                // condition $lte not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions =
                        listOf(
                            DocumentCondition(
                                field = "age",
                                operator = "\$lte",
                                value = "19"
                            )
                        )
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

    @Test
    fun `should always publish APP notification for every matched rule`() {
        // given
        whenever(notificationConfigCache.findAll()).thenReturn(
            listOf(
                NotificationConfigCacheEntry(
                    channelPush = false,
                    channelEmail = false,
                    userIdentifier = "user-1",
                    rules = listOf(
                        NotificationRuleCacheEntry(
                            label = "Rule 1",
                            externalIdentifier = "ext-1",
                            notificationType = NotificationType.ENTITY_CHANGE,
                            entityType = "Child",
                            changeType = "created",
                            conditions = emptyList(),
                            enabled = true
                        )
                    )
                )
            )
        )

        // when
        val result = service.run(
            ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent)
        )

        // then
        assertThat(result).isInstanceOf(UseCaseOutcome.Success::class.java)

        val eventCaptor = argumentCaptor<CreateUserNotificationEvent>()
        verify(userNotificationPublisher, times(2)).publish(
            eq(USER_NOTIFICATION_QUEUE),
            eventCaptor.capture()
        )
        assertThat(eventCaptor.allValues.map { it.notificationChannelType })
            .containsExactlyInAnyOrder(
                NotificationChannelType.APP,
                NotificationChannelType.PUSH // currently always sent regardless of the channelPush config flag, see TODO in use case
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
                    rules = listOf(
                        NotificationRuleCacheEntry(
                            label = "Rule 1",
                            externalIdentifier = "ext-1",
                            notificationType = NotificationType.ENTITY_CHANGE,
                            entityType = "Child",
                            changeType = "created",
                            conditions = emptyList(),
                            enabled = true
                        )
                    )
                )
            )
        )

        // when
        val result = service.run(
            ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent)
        )

        // then
        assertThat(result).isInstanceOf(UseCaseOutcome.Success::class.java)

        val eventCaptor = argumentCaptor<CreateUserNotificationEvent>()
        verify(userNotificationPublisher, times(2)).publish(
            eq(USER_NOTIFICATION_QUEUE),
            eventCaptor.capture()
        )
        val channelTypes = eventCaptor.allValues.map { it.notificationChannelType }
        assertThat(channelTypes).containsExactlyInAnyOrder(
            NotificationChannelType.APP,
            NotificationChannelType.PUSH
        )
    }

    @Disabled("Email channel not yet implemented")
    @Test
    fun `should also publish EMAIL notification when channelEmail flag is enabled`() {
        // given
        whenever(notificationConfigCache.findAll()).thenReturn(
            listOf(
                NotificationConfigCacheEntry(
                    channelPush = false,
                    channelEmail = true,
                    userIdentifier = "user-1",
                    rules = listOf(
                        NotificationRuleCacheEntry(
                            label = "Rule 1",
                            externalIdentifier = "ext-1",
                            notificationType = NotificationType.ENTITY_CHANGE,
                            entityType = "Child",
                            changeType = "created",
                            conditions = emptyList(),
                            enabled = true
                        )
                    )
                )
            )
        )

        // when
        val result = service.run(
            ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent)
        )

        // then
        assertThat(result).isInstanceOf(UseCaseOutcome.Success::class.java)

        val eventCaptor = argumentCaptor<CreateUserNotificationEvent>()
        verify(userNotificationPublisher, times(3)).publish(
            eq(USER_NOTIFICATION_QUEUE),
            eventCaptor.capture()
        )
        val channelTypes = eventCaptor.allValues.map { it.notificationChannelType }
        assertThat(channelTypes).containsExactlyInAnyOrder(
            NotificationChannelType.APP,
            NotificationChannelType.PUSH, // currently always sent regardless of the channelPush config flag, see TODO in use case
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
        val result = service.run(
            ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent)
        )

        // then
        assertThat(result).isInstanceOf(UseCaseOutcome.Success::class.java)
        assertEquals(0, (result as UseCaseOutcome.Success).data.notificationsSendCount)
        verify(userNotificationPublisher, times(0)).publish(any(), any())
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
        val result = service.run(
            ApplyNotificationRulesRequest(documentChangeEvent = documentCreateEvent)
        )

        // then
        assertThat(result).isInstanceOf(UseCaseOutcome.Success::class.java)
        assertEquals(0, (result as UseCaseOutcome.Success).data.notificationsSendCount)
        verify(userNotificationPublisher, times(0)).publish(any(), any())
    }
}
