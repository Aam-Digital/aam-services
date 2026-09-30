package com.aamdigital.aambackendservice.common.rest

import com.aamdigital.aambackendservice.common.changes.SyncEntry
import com.aamdigital.aambackendservice.common.couchdb.dto.AttachmentMetaData
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.EntityAttributeType
import com.aamdigital.aambackendservice.common.domain.UpdateMetadata
import com.aamdigital.aambackendservice.common.error.HttpErrorDto
import com.aamdigital.aambackendservice.common.mail.MailSenderRequest
import com.aamdigital.aambackendservice.common.outbox.OutboxEntry
import com.aamdigital.aambackendservice.common.permission.core.PermissionCheckRequest
import com.aamdigital.aambackendservice.common.storage.migration.MigrationStepState
import com.aamdigital.aambackendservice.export.controller.TemplateExportControllerResponse
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.core.create.app.NotificationEventDto
import com.aamdigital.aambackendservice.notification.domain.EntityNotificationContext
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationDetails
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import com.aamdigital.aambackendservice.notification.repository.UserDeviceEntity
import com.aamdigital.aambackendservice.reporting.report.sqs.QueryRequest
import com.aamdigital.aambackendservice.reporting.report.sqs.SqlObject
import com.aamdigital.aambackendservice.reporting.report.sqs.SqlOptions
import com.aamdigital.aambackendservice.reporting.report.sqs.SqsSchema
import com.aamdigital.aambackendservice.reporting.report.sqs.TableFields
import com.aamdigital.aambackendservice.reporting.report.sqs.TableName
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.reportcalculation.controller.ReportCalculationData
import com.aamdigital.aambackendservice.reporting.reportcalculation.controller.ReportCalculationDto
import com.aamdigital.aambackendservice.reporting.reportcalculation.storage.ReportCalculationEntity
import com.aamdigital.aambackendservice.reporting.webhook.WebhookAuthenticationType
import com.aamdigital.aambackendservice.reporting.webhook.WebhookTarget
import com.aamdigital.aambackendservice.reporting.webhook.controller.WebhookAuthenticationReadDto
import com.aamdigital.aambackendservice.reporting.webhook.controller.WebhookDto
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookAuthenticationEntity
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookEntity
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookOwner
import com.aamdigital.aambackendservice.skill.domain.EscoSkill
import com.aamdigital.aambackendservice.skill.domain.SkillUsage
import com.aamdigital.aambackendservice.skill.domain.UserProfile
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileEntity
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileSyncEntity
import com.aamdigital.aambackendservice.skill.repository.SkillReferenceEntity
import com.aamdigital.aambackendservice.thirdpartyauthentication.repository.ThirdPartyAuthSession
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Date
import java.util.UUID

/**
 * Pins the JSON the application writes, byte for byte, to CouchDB and to API clients.
 *
 * ndb-core, the e2e contract checks and documents already stored in CouchDB all depend on this
 * exact shape: date and time values as numbers unless annotated otherwise, properties in
 * declaration order, nulls written out, and characters outside the Basic Multilingual Plane
 * escaped in UTF-8 output. The expected values were recorded from the Jackson 2 mapper the
 * application used before it moved to Jackson 3.
 */
class JsonWireFormatTest {
    companion object {
        private val INSTANT: Instant = Instant.parse("2026-09-28T10:15:30.123456789Z")
        private val INSTANT_MILLIS: Instant = Instant.parse("2026-09-28T10:15:30.120Z")
        private val INSTANT_SECONDS: Instant = Instant.parse("2026-09-28T10:15:30Z")
        private val OFFSET_DATE_TIME: OffsetDateTime = OffsetDateTime.parse("2026-09-28T12:15:30.5+02:00")
        private val UTC_DATE_TIME: OffsetDateTime = OffsetDateTime.parse("2026-09-28T10:15:30Z")
        private val NOTIFICATION_ID: UUID = UUID.fromString("0b6f0a52-5c1e-4d3c-9d6e-1f7f5b2b8a11")
    }

    private val mapper = applicationJsonMapper()

    @Test
    fun `should write an outbox entry with numeric dates, nulls and escaped supplementary characters`() {
        // Given
        val entry =
            OutboxEntry(
                id = "OutboxEntry:key-1",
                payload =
                    CreateUserNotificationEvent(
                        userIdentifier = "user-1",
                        notificationChannelType = NotificationChannelType.EMAIL,
                        notificationRule = "rule-1",
                        details =
                            NotificationDetails(
                                id = NOTIFICATION_ID,
                                notificationType = NotificationType.ENTITY_CHANGE,
                                title = "Child \"Ada\" updated \uD83D\uDE00",
                                body = null,
                                actionUrl = "/child/1?tab=2&x=<y>",
                                context = EntityNotificationContext(entityType = "Child", entityId = null),
                                created = INSTANT
                            )
                    ),
                attempts = 2,
                nextAttemptAt = INSTANT_SECONDS,
                lastError = null,
                createdAt = INSTANT_MILLIS
            )

        // When
        val json = String(mapper.writeValueAsBytes(entry), Charsets.UTF_8)

        // Then
        assertThat(json).isEqualTo(
            """{"_id":"OutboxEntry:key-1","payload":{"userIdentifier":"user-1","notificationChannelType":"EMAIL","notificationRule":"rule-1","details":{"id":"0b6f0a52-5c1e-4d3c-9d6e-1f7f5b2b8a11","notificationType":"entity_change","title":"Child \"Ada\" updated \uD83D\uDE00","body":null,"actionUrl":"/child/1?tab=2&x=<y>","context":{"entityType":"Child","entityId":null},"created":1790590530.123456789}},"attempts":2,"nextAttemptAt":1790590530.000000000,"lastError":null,"createdAt":1790590530.120000000}"""
        )
    }

    @Test
    fun `should write the in-app notification document ndb-core reads`() {
        // Given
        val event =
            NotificationEventDto(
                id = "NotificationEvent:$NOTIFICATION_ID",
                title = "Child updated",
                body = null,
                actionUrl = null,
                notificationType = "entity_change",
                context = EntityNotificationContext(entityType = "Child", entityId = "Child:1"),
                created = UpdateMetadata(at = INSTANT.toString(), by = "system")
            )

        // When
        val json = mapper.writeValueAsString(event)

        // Then
        assertThat(json).isEqualTo(
            """{"_id":"NotificationEvent:0b6f0a52-5c1e-4d3c-9d6e-1f7f5b2b8a11","title":"Child updated","body":null,"actionUrl":null,"notificationType":"entity_change","context":{"entityType":"Child","entityId":"Child:1"},"created":{"at":"2026-09-28T10:15:30.123456789Z","by":"system"}}"""
        )
    }

    @Test
    fun `should write a webhook document with a numeric creation date`() {
        // Given
        val webhook =
            WebhookEntity(
                id = "Webhook:1",
                label = "Report webhook",
                target = WebhookTarget(method = "POST", url = "https://example.com/hook?a=1&b=2"),
                authentication =
                    WebhookAuthenticationEntity(
                        type = WebhookAuthenticationType.API_KEY,
                        iv = "iv-1",
                        data = "encrypted"
                    ),
                owner = WebhookOwner(creator = "user-1"),
                reportSubscriptions = mutableListOf("ReportConfig:1"),
                createdAt = INSTANT
            )

        // When
        val json = mapper.writeValueAsString(listOf(webhook, webhook.copy(createdAt = null)))

        // Then
        assertThat(json).isEqualTo(
            """[{"id":"Webhook:1","label":"Report webhook","target":{"method":"POST","url":"https://example.com/hook?a=1&b=2"},"authentication":{"type":"API_KEY","iv":"iv-1","data":"encrypted"},"owner":{"creator":"user-1","users":[],"groups":[],"roles":[]},"reportSubscriptions":["ReportConfig:1"],"createdAt":1790590530.123456789},{"id":"Webhook:1","label":"Report webhook","target":{"method":"POST","url":"https://example.com/hook?a=1&b=2"},"authentication":{"type":"API_KEY","iv":"iv-1","data":"encrypted"},"owner":{"creator":"user-1","users":[],"groups":[],"roles":[]},"reportSubscriptions":["ReportConfig:1"],"createdAt":null}]"""
        )
    }

    @Test
    fun `should write the webhook API response with the creation date as a string`() {
        // Given
        val webhook =
            WebhookDto(
                id = "Webhook:1",
                label = "Report webhook",
                target = WebhookTarget(method = "POST", url = "https://example.com/hook"),
                authentication = WebhookAuthenticationReadDto(type = "API_KEY"),
                owner = WebhookOwner(creator = "user-1", users = listOf("user-2")),
                reportSubscriptions = mutableListOf(),
                createdAt = INSTANT
            )

        // When
        val json = mapper.writeValueAsString(listOf(webhook, webhook.copy(createdAt = null)))

        // Then
        assertThat(json).isEqualTo(
            """[{"id":"Webhook:1","label":"Report webhook","target":{"method":"POST","url":"https://example.com/hook"},"authentication":{"type":"API_KEY"},"owner":{"creator":"user-1","users":["user-2"],"groups":[],"roles":[]},"reportSubscriptions":[],"createdAt":"2026-09-28T10:15:30.123456789Z"},{"id":"Webhook:1","label":"Report webhook","target":{"method":"POST","url":"https://example.com/hook"},"authentication":{"type":"API_KEY"},"owner":{"creator":"user-1","users":["user-2"],"groups":[],"roles":[]},"reportSubscriptions":[],"createdAt":null}]"""
        )
    }

    @Test
    fun `should write offset date times annotated as strings in the documents that carry them`() {
        // Given
        val documents =
            listOf(
                UserDeviceEntity(
                    deviceName = null,
                    deviceToken = "token-1",
                    userIdentifier = "user-1",
                    createdAt = OFFSET_DATE_TIME
                ),
                SkillLabUserProfileEntity(
                    externalIdentifier = "external-1",
                    fullName = "Ada Lovelace",
                    mobileNumber = null,
                    email = "ada@example.com",
                    skills =
                        setOf(
                            SkillReferenceEntity(
                                externalIdentifier = "skill-1",
                                escoUri = "http://data.europa.eu/esco/skill/1",
                                usage = "ALWAYS"
                            )
                        ),
                    updatedAt = "2026-09-01T00:00:00.000Z",
                    latestSyncAt = OFFSET_DATE_TIME,
                    importedAt = UTC_DATE_TIME
                ),
                SkillLabUserProfileSyncEntity(projectId = "project-1", latestSync = UTC_DATE_TIME)
            )

        // When
        val json = mapper.writeValueAsString(documents)

        // Then
        assertThat(json).isEqualTo(
            """[{"deviceName":null,"deviceToken":"token-1","userIdentifier":"user-1","createdAt":"2026-09-28T12:15:30.5+02:00"},{"externalIdentifier":"external-1","fullName":"Ada Lovelace","mobileNumber":null,"email":"ada@example.com","skills":[{"externalIdentifier":"skill-1","escoUri":"http://data.europa.eu/esco/skill/1","usage":"ALWAYS"}],"updatedAt":"2026-09-01T00:00:00.000Z","latestSyncAt":"2026-09-28T12:15:30.5+02:00","importedAt":"2026-09-28T10:15:30Z"},{"projectId":"project-1","latestSync":"2026-09-28T10:15:30Z"}]"""
        )
    }

    @Test
    fun `should write instants annotated as strings in the documents that carry them`() {
        // Given
        val documents =
            listOf(
                ThirdPartyAuthSession(
                    sessionId = "session-1",
                    userId = "user-1",
                    redirectUrl = null,
                    createdAt = INSTANT
                ),
                ThirdPartyAuthSession(sessionId = "session-2", userId = "user-1", createdAt = INSTANT_SECONDS),
                MigrationStepState(step = "postgres-to-couchdb", completedAt = INSTANT_MILLIS)
            )

        // When
        val json = mapper.writeValueAsString(documents)

        // Then
        assertThat(json).isEqualTo(
            """[{"sessionId":"session-1","userId":"user-1","redirectUrl":null,"createdAt":"2026-09-28T10:15:30.123456789Z"},{"sessionId":"session-2","userId":"user-1","redirectUrl":null,"createdAt":"2026-09-28T10:15:30Z"},{"step":"postgres-to-couchdb","completedAt":"2026-09-28T10:15:30.120Z"}]"""
        )
    }

    @Test
    fun `should write a report calculation document with its attachment metadata`() {
        // Given
        val calculation =
            ReportCalculationEntity(
                id = "ReportCalculation:1",
                report = DomainReference(id = "ReportConfig:1"),
                status = ReportCalculationStatus.FINISHED_SUCCESS,
                errorDetails = null,
                calculationStarted = "2026-09-28T10:15:30.123Z",
                calculationCompleted = null,
                args = mutableMapOf("from" to "2026-01-01", "to" to "2026-12-31"),
                attachments =
                    mutableMapOf(
                        "data.json" to
                            AttachmentMetaData(
                                contentType = "application/json",
                                revpos = 2,
                                digest = "md5-abc==",
                                length = 12345678901L,
                                stub = true
                            )
                    ),
                fromAutomaticChangeDetection = true
            )

        // When
        val json = mapper.writeValueAsString(calculation)

        // Then
        assertThat(json).isEqualTo(
            """{"_id":"ReportCalculation:1","report":{"id":"ReportConfig:1"},"status":"FINISHED_SUCCESS","errorDetails":null,"calculationStarted":"2026-09-28T10:15:30.123Z","calculationCompleted":null,"args":{"from":"2026-01-01","to":"2026-12-31"},"_attachments":{"data.json":{"content_type":"application/json","revpos":2,"digest":"md5-abc==","length":12345678901,"stub":true}},"fromAutomaticChangeDetection":true}"""
        )
    }

    @Test
    fun `should write the SQS schema and derive the same config version from it`() {
        // Given
        val schema =
            SqsSchema(
                sql =
                    SqlObject(
                        tables =
                            mapOf(
                                "Child" to
                                    TableFields(
                                        mapOf(
                                            "name" to EntityAttributeType(field = "name", type = "TEXT"),
                                            "_created_at" to EntityAttributeType(field = "created.at", type = "DATE")
                                        )
                                    ),
                                "ConfigurableEnum" to TableFields(emptyMap())
                            ),
                        indexes = listOf("options_view"),
                        options = SqlOptions(TableName(field = "_id", separator = ":"))
                    )
            )

        // When
        val json = mapper.writeValueAsString(schema)

        // Then
        assertThat(schema.configVersion).isEqualTo("8d3360a6b8da0e55f7a83601f7010d30e67bfb53dbdf6dcae950fdd5b16182b6")
        assertThat(json).isEqualTo(
            """{"language":"sqlite","sql":{"tables":{"Child":{"fields":{"name":{"field":"name","type":"TEXT"},"_created_at":{"field":"created.at","type":"DATE"}}},"ConfigurableEnum":{"fields":{}}},"indexes":["options_view"],"options":{"table_name":{"operation":"prefix","field":"_id","separator":":"}}},"configVersion":"8d3360a6b8da0e55f7a83601f7010d30e67bfb53dbdf6dcae950fdd5b16182b6"}"""
        )
    }

    @Test
    fun `should write change detection cursors and one-off request bodies in declaration order`() {
        // Given
        val bodies =
            listOf(
                SyncEntry(database = "app", latestRef = "12-g1AAAA", consumer = "notification"),
                SyncEntry(database = "app", latestRef = "12-g1AAAA"),
                PermissionCheckRequest(userIds = listOf("user-1", "user-2"), entityId = "Child:1", action = "read"),
                QueryRequest(query = "SELECT * FROM Child WHERE name = ?", args = listOf("Ada")),
                MailSenderRequest(
                    to = "ada@example.com",
                    subject = "Hello",
                    body = "<p>Hi</p>",
                    isHtml = true,
                    headers = mapOf("List-Unsubscribe" to "<https://example.com/u>")
                )
            )

        // When
        val json = mapper.writeValueAsString(bodies)

        // Then
        assertThat(json).isEqualTo(
            """[{"database":"app","latestRef":"12-g1AAAA","consumer":"notification"},{"database":"app","latestRef":"12-g1AAAA","consumer":null},{"userIds":["user-1","user-2"],"entityId":"Child:1","action":"read"},{"query":"SELECT * FROM Child WHERE name = ?","args":["Ada"]},{"to":"ada@example.com","subject":"Hello","body":"<p>Hi</p>","from":"","isHtml":true,"headers":{"List-Unsubscribe":"<https://example.com/u>"}}]"""
        )
    }

    @Test
    fun `should write the skill API user profile with numeric instants and enum names`() {
        // Given
        val profile =
            UserProfile(
                id = "external-1",
                fullName = "Ada Lovelace",
                phone = null,
                email = "ada@example.com",
                skills =
                    listOf(
                        EscoSkill(escoUri = "http://data.europa.eu/esco/skill/1", usage = SkillUsage.BI_WEEKLY),
                        EscoSkill(escoUri = "http://data.europa.eu/esco/skill/2", usage = SkillUsage.UNKNOWN)
                    ),
                updatedAtExternalSystem = null,
                importedAt = INSTANT,
                latestSyncAt = null
            )

        // When
        val json = mapper.writeValueAsString(profile)

        // Then
        assertThat(json).isEqualTo(
            """{"id":"external-1","fullName":"Ada Lovelace","phone":null,"email":"ada@example.com","skills":[{"escoUri":"http://data.europa.eu/esco/skill/1","usage":"BI-WEEKLY"},{"escoUri":"http://data.europa.eu/esco/skill/2","usage":"UNKNOWN"}],"updatedAtExternalSystem":null,"importedAt":1790590530.123456789,"latestSyncAt":null}"""
        )
    }

    @Test
    fun `should write report calculation and error responses`() {
        // Given
        val responses =
            listOf(
                ReportCalculationDto(
                    id = "ReportCalculation:1",
                    report = DomainReference(id = "ReportConfig:1"),
                    status = ReportCalculationStatus.FINISHED_ERROR,
                    startDate = "2026-09-28T10:15:30.123Z",
                    endDate = null,
                    args = mapOf("from" to "2026-01-01"),
                    data = ReportCalculationData(contentType = "application/json", hash = "md5-abc==", length = 42L),
                    errorDetails = "near \"FROM\": syntax error"
                ),
                TemplateExportControllerResponse.ErrorControllerResponse(
                    errorCode = "NOT_FOUND_ERROR",
                    errorMessage = "Template </script> not found"
                ),
                HttpErrorDto(errorCode = "forbidden", errorMessage = "access denied")
            )

        // When
        val json = mapper.writeValueAsString(responses)

        // Then
        assertThat(json).isEqualTo(
            """[{"id":"ReportCalculation:1","report":{"id":"ReportConfig:1"},"status":"FINISHED_ERROR","startDate":"2026-09-28T10:15:30.123Z","endDate":null,"args":{"from":"2026-01-01"},"data":{"contentType":"application/json","hash":"md5-abc==","length":42},"errorDetails":"near \"FROM\": syntax error"},{"errorCode":"NOT_FOUND_ERROR","errorMessage":"Template </script> not found"},{"errorCode":"forbidden","errorMessage":"access denied"}]"""
        )
    }

    @Test
    fun `should write maps with null values and dates the way the legacy report migration and error pages rely on`() {
        // Given
        val body =
            mapOf(
                "_id" to "ReportConfig:1",
                "mode" to "sql",
                "transformations" to mapOf("startDate" to listOf("SQL_FROM_DATE")),
                "reportDefinition" to listOf(mapOf("query" to "SELECT 1")),
                "legacyOriginal" to
                    mapOf("aggregationDefinition" to "SELECT ?", "neededArgs" to null, "version" to null),
                "timestamp" to Date.from(INSTANT_MILLIS),
                "instant" to INSTANT,
                "offsetDateTime" to OFFSET_DATE_TIME,
                "notificationType" to NotificationType.ENTITY_CHANGE,
                "usage" to SkillUsage.BI_WEEKLY,
                "id" to NOTIFICATION_ID
            )

        // When
        val json = mapper.writeValueAsString(body)

        // Then
        assertThat(json).isEqualTo(
            """{"_id":"ReportConfig:1","mode":"sql","transformations":{"startDate":["SQL_FROM_DATE"]},"reportDefinition":[{"query":"SELECT 1"}],"legacyOriginal":{"aggregationDefinition":"SELECT ?","neededArgs":null,"version":null},"timestamp":1790590530120,"instant":1790590530.123456789,"offsetDateTime":1790590530.500000000,"notificationType":"entity_change","usage":"BI-WEEKLY","id":"0b6f0a52-5c1e-4d3c-9d6e-1f7f5b2b8a11"}"""
        )
    }

    @Test
    fun `should write a CouchDB document read as a tree back with its numbers and text unchanged`() {
        // Given
        val document =
            mapper.readTree(
                """
                {"_id":"Child:1","_rev":"3-abc","int":1,"long":12345678901,"big":123456789012345678901234567890,
                "double":1.0,"decimal":1.50,"exponent":1e2,"small":0.1,"negative":-0.0,"text":"\u00e9 \uD83D\uDE00 \u2028 </",
                "nothing":null,"empty":[],"nested":{"b":2,"a":[1,"x",null,true]}}
                """.trimIndent()
            )

        // When
        val json = mapper.writeValueAsString(document)
        val bytes = String(mapper.writeValueAsBytes(document), Charsets.UTF_8)

        // Then
        assertThat(json).isEqualTo(
            "{\"_id\":\"Child:1\",\"_rev\":\"3-abc\",\"int\":1,\"long\":12345678901," +
                "\"big\":123456789012345678901234567890,\"double\":1.0,\"decimal\":1.5," +
                "\"exponent\":100.0,\"small\":0.1,\"negative\":-0.0," +
                "\"text\":\"\u00e9 \uD83D\uDE00 \u2028 </\"," +
                "\"nothing\":null,\"empty\":[],\"nested\":{\"b\":2,\"a\":[1,\"x\",null,true]}}"
        )
        assertThat(bytes).isEqualTo(
            "{\"_id\":\"Child:1\",\"_rev\":\"3-abc\",\"int\":1,\"long\":12345678901," +
                "\"big\":123456789012345678901234567890,\"double\":1.0,\"decimal\":1.5," +
                "\"exponent\":100.0,\"small\":0.1,\"negative\":-0.0," +
                "\"text\":\"\u00e9 \\uD83D\\uDE00 \u2028 </\"," +
                "\"nothing\":null,\"empty\":[],\"nested\":{\"b\":2,\"a\":[1,\"x\",null,true]}}"
        )
    }

    @Test
    fun `should write a Kotlin object without properties as an empty object but refuse a bare Object`() {
        // When
        val json = mapper.writeValueAsString(EmptyBody())

        // Then
        assertThat(json).isEqualTo("{}")
        assertThatThrownBy { mapper.writeValueAsString(Any()) }.hasMessageContaining("java.lang.Object")
    }

    class EmptyBody
}
