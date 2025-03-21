package com.aamdigital.aambackendservice.notification.core.trigger

import com.aamdigital.aambackendservice.changes.domain.DocumentChangeEvent
import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import com.aamdigital.aambackendservice.notification.queue.UserNotificationPublisher
import com.aamdigital.aambackendservice.notification.repository.NotificationConditionEntity
import com.aamdigital.aambackendservice.notification.repository.NotificationConfigEntity
import com.aamdigital.aambackendservice.notification.repository.NotificationConfigRepository
import com.aamdigital.aambackendservice.notification.repository.NotificationRuleEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class DefaultApplyNotificationRulesUseCaseTest {
    private lateinit var service: DefaultApplyNotificationRulesUseCase

    @Mock
    lateinit var notificationConfigRepository: NotificationConfigRepository

    @Mock
    lateinit var userNotificationPublisher: UserNotificationPublisher

    @BeforeEach
    fun setUp() {
        reset(
            notificationConfigRepository,
            userNotificationPublisher,
        )

        service = DefaultApplyNotificationRulesUseCase(
            notificationConfigRepository = notificationConfigRepository,
            userNotificationPublisher = userNotificationPublisher,
        )
    }

    private val documentUpdateEvent = DocumentChangeEvent(
        database = "app",
        documentId = "Child:14b69515-1d64-449a-802e-6eee0591021b",
        rev = "2-e5d65d1843b97422aaaa29ad3bfba900",
        currentVersion = mapOf(
            "_id" to "Child:1",
            "_rev" to "2-e5d65d1843b97422aaaa29ad3bfba900",
            "created" to mapOf("at" to "2025-03-14T15:37:09.855Z"),
            "updated" to mapOf("at" to "2025-03-14T15:37:09.855Z"),
            "name" to "Bert",
            "age" to 18,
            "categories" to listOf("X", "Y")
        ),
        previousVersion = mapOf(
            "_id" to "Child:1",
            "_rev" to "1-a8358a730180c52af9b6973acd334e67",
            "created" to mapOf("at" to "2025-03-14T15:37:09.855Z"),
            "updated" to mapOf("at" to "2025-03-14T15:37:09.855Z"),
            "age" to 17,
            "name" to "Adam"
        ),
        deleted = false
    )

    private val documentCreateEvent = DocumentChangeEvent(
        database = "app",
        documentId = "Child:1",
        rev = "1-aa4598f080dc7db187cc3f59c92fd7fd",
        currentVersion = mapOf(
            "_id" to "Child:1",
            "_rev" to "1-aa4598f080dc7db187cc3f59c92fd7fd",
            "name" to "New Doc",
        ),
        previousVersion = emptyMap<String, Any>(),
        deleted = false
    )

    private val documentDeleteEvent = DocumentChangeEvent(
        database = "app",
        documentId = "Child:1",
        rev = "3-dc4f892af6233d08f67f8817d2e28bb2",
        currentVersion = emptyMap<String, Any>(),
        previousVersion = emptyMap<String, Any>(),
        deleted = true
    )

    private val documentOtherUpdateEvent = DocumentChangeEvent(
        database = "app",
        documentId = "School:1",
        rev = "2-e5d65d1843b97422aaaa29ad3bfba900",
        currentVersion = mapOf(
            "_id" to "School:1",
            "_rev" to "2-e5d65d1843b97422aaaa29ad3bfba900",
            "created" to mapOf("at" to "2025-03-14T15:37:09.855Z"),
            "updated" to mapOf("at" to "2025-03-14T15:37:09.855Z"),
            "name" to "Other School +"
        ),
        previousVersion = mapOf(
            "_id" to "School:1",
            "_rev" to "1-a8358a730180c52af9b6973acd334e67",
            "created" to mapOf("at" to "2025-03-14T15:37:09.855Z"),
            "updated" to mapOf("at" to "2025-03-14T15:37:09.855Z"),
            "name" to "Other School"
        ),
        deleted = false
    )

    private val documentChangeEvents = mapOf(
        "created" to documentCreateEvent,
        "updated" to documentUpdateEvent,
        "deleted" to documentDeleteEvent,
        "other" to documentOtherUpdateEvent,
    )

    private fun generateNotificationConfig(
        changeType: String,
        conditions: List<NotificationConditionEntity> = emptyList(),
        entityType: String = "Child",
        enabled: Boolean = true,
    ): NotificationConfigEntity {
        return NotificationConfigEntity(
            id = 1,
            channelPush = true,
            channelEmail = true,
            revision = "rev-1",
            userIdentifier = "user-1",
            notificationRules = listOf(
                NotificationRuleEntity(
                    notificationType = NotificationType.ENTITY_CHANGE,
                    id = 2,
                    label = "Notification Rule 1",
                    externalIdentifier = "external-identifier-1",
                    entityType = entityType,
                    changeType = changeType,
                    conditions = conditions,
                    enabled = enabled,
                )
            ),
            createdAt = Instant.parse("2025-03-14T15:37:09.855Z").atOffset(ZoneOffset.UTC),
            updatedAt = Instant.parse("2025-03-14T15:37:09.855Z").atOffset(ZoneOffset.UTC)
        )
    }

    @ParameterizedTest
    @CsvSource(
        "created,1",
        "updated,0",
        "deleted,0",
        "other,0",
    )
    fun `should trigger notification when child is created`(documentEventType: String, notificationsSendCount: Int) {
        // given
        whenever(notificationConfigRepository.findAll()).thenReturn(
            listOf(
                generateNotificationConfig(changeType = "created")
            )
        )

        // when
        val result = service.run(
            ApplyNotificationRulesRequest(
                documentChangeEvent = documentChangeEvents[documentEventType]!!,
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
        "other,0",
    )
    fun `should trigger notification when child is updated`(documentEventType: String, notificationsSendCount: Int) {
        // given
        whenever(notificationConfigRepository.findAll()).thenReturn(
            listOf(
                generateNotificationConfig(
                    changeType = "updated",
                ),
                generateNotificationConfig(
                    changeType = "created",
                    enabled = false,
                )
            )
        )

        // when
        val result = service.run(
            ApplyNotificationRulesRequest(
                documentChangeEvent = when (documentEventType) {
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
        "other,0",
    )
    fun `should trigger notification when child is deleted`(documentEventType: String, notificationsSendCount: Int) {
        // given
        whenever(notificationConfigRepository.findAll()).thenReturn(
            listOf(
                generateNotificationConfig(changeType = "deleted")
            )
        )

        // when
        val result = service.run(
            ApplyNotificationRulesRequest(
                documentChangeEvent = documentChangeEvents[documentEventType]!!,
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
        "other,0",
    )
    fun `should trigger notification when child is updated with matching condition`(
        documentEventType: String,
        notificationsSendCount: Int
    ) {
        // given
        whenever(notificationConfigRepository.findAll()).thenReturn(
            listOf(
                // condition $eq matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = listOf(
                        NotificationConditionEntity(
                            field = "name",
                            operator = "\$eq",
                            value = "Bert",
                        )
                    )
                ),
                // condition $eq not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = listOf(
                        NotificationConditionEntity(
                            field = "name",
                            operator = "\$eq",
                            value = "Clark",
                        )
                    )
                ),

                // condition $nq matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = listOf(
                        NotificationConditionEntity(
                            field = "name",
                            operator = "\$nq",
                            value = "XYZ",
                        )
                    )
                ),
                // condition $nq not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = listOf(
                        NotificationConditionEntity(
                            field = "name",
                            operator = "\$nq",
                            value = "Bert",
                        )
                    )
                ),

                // condition $elemMatch matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = listOf(
                        NotificationConditionEntity(
                            field = "categories",
                            operator = "\$elemMatch",
                            value = "X",
                        )
                    )
                ),
                // condition $elemMatch not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = listOf(
                        NotificationConditionEntity(
                            field = "categories",
                            operator = "\$elemMatch",
                            value = "ABC",
                        )
                    )
                ),

                // condition $gt matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = listOf(
                        NotificationConditionEntity(
                            field = "age",
                            operator = "\$gt",
                            value = "19",
                        )
                    )
                ),
                // condition $gt not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = listOf(
                        NotificationConditionEntity(
                            field = "age",
                            operator = "\$gt",
                            value = "18",
                        )
                    )
                ),

                // condition $gte matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = listOf(
                        NotificationConditionEntity(
                            field = "age",
                            operator = "\$gte",
                            value = "18",
                        )
                    )
                ),
                // condition $gte not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = listOf(
                        NotificationConditionEntity(
                            field = "age",
                            operator = "\$gte",
                            value = "17",
                        )
                    )
                ),

                // condition $lt matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = listOf(
                        NotificationConditionEntity(
                            field = "age",
                            operator = "\$lt",
                            value = "17",
                        )
                    )
                ),
                // condition $lt not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = listOf(
                        NotificationConditionEntity(
                            field = "age",
                            operator = "\$lt",
                            value = "18",
                        )
                    )
                ),

                // condition $lte matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = listOf(
                        NotificationConditionEntity(
                            field = "age",
                            operator = "\$lte",
                            value = "18",
                        )
                    )
                ),
                // condition $lte not matching:
                generateNotificationConfig(
                    changeType = "updated",
                    conditions = listOf(
                        NotificationConditionEntity(
                            field = "age",
                            operator = "\$lte",
                            value = "19",
                        )
                    )
                ),
            )
        )

        // when
        val result = service.run(
            ApplyNotificationRulesRequest(
                documentChangeEvent = documentChangeEvents[documentEventType]!!,
            )
        )

        // then
        assertThat(result).isInstanceOf(UseCaseOutcome.Success::class.java)
        assertEquals(notificationsSendCount, (result as UseCaseOutcome.Success).data.notificationsSendCount)
    }
}
