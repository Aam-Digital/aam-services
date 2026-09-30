package com.aamdigital.aambackendservice.common.rest

import com.aamdigital.aambackendservice.common.auth.core.KeycloakTokenResponse
import com.aamdigital.aambackendservice.common.changes.SyncEntryDocument
import com.aamdigital.aambackendservice.common.couchdb.dto.CouchDbChangesResponse
import com.aamdigital.aambackendservice.common.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.common.outbox.OutboxEntry
import com.aamdigital.aambackendservice.common.permission.core.PermissionCheckResult
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.core.config.NotificationConfigDto
import com.aamdigital.aambackendservice.notification.core.config.NotificationRuleDto
import com.aamdigital.aambackendservice.notification.repository.UserDeviceEntity
import com.aamdigital.aambackendservice.reporting.report.sqs.AppConfigFile
import com.aamdigital.aambackendservice.reporting.report.sqs.SqsSchema
import com.aamdigital.aambackendservice.reporting.report.storage.ReportConfigEntity
import com.aamdigital.aambackendservice.reporting.reportcalculation.storage.ReportCalculationEntity
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookEntity
import com.aamdigital.aambackendservice.skill.domain.SkillUsage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Pins how leniently the application reads JSON: documents in CouchDB are written by other
 * versions of this service, by ndb-core and by hand, and carry `_rev` and fields no class here
 * models. Each outcome is either the object read, as its `toString()`, or "fails".
 *
 * The expected values were recorded from the Jackson 2 mapper the application used before it moved
 * to Jackson 3.
 */
class JsonReadingTest {
    companion object {
        private const val RULE = """"label":"Rule","entityType":"Child","conditions":{"name":"Ada"}"""

        /** Notification rules as users may have saved them, one deviation from the model each. */
        private val NOTIFICATION_RULES =
            listOf(
                """{$RULE,"notificationType":"entity_change","changeType":["created"],"enabled":true}""",
                """{$RULE,"notificationType":"entity_change","changeType":["created"]}""",
                """{$RULE,"notificationType":"entity_change","changeType":["created"],"enabled":null}""",
                """{$RULE,"notificationType":"entity_change","changeType":["created",null],"enabled":true}""",
                """{$RULE,"notificationType":"entity_change","changeType":"created","enabled":true}""",
                """{$RULE,"notificationType":"a_type_added_later","changeType":[],"enabled":true}""",
                """{$RULE,"notificationType":"","changeType":[],"enabled":true}""",
                """{$RULE,"notificationType":null,"changeType":[],"enabled":true}""",
                """{$RULE,"notificationType":"entity_change","changeType":[],"enabled":"true"}""",
                """{$RULE,"notificationType":"entity_change","changeType":[],"enabled":1}""",
                """{$RULE,"notificationType":"entity_change","changeType":null,"enabled":true}""",
                """{"label":"Rule","entityType":"Child","notificationType":"entity_change","changeType":[],"enabled":true}""",
                """{"label":"Rule","entityType":"Child","conditions":null,"notificationType":"entity_change","changeType":[],"enabled":true}""",
                """{"label":null,"entityType":"Child","conditions":{},"notificationType":"entity_change","changeType":[],"enabled":true}""",
                """{"Label":"Rule","entityType":"Child","conditions":{},"notificationType":"entity_change","changeType":[],"enabled":true}""",
                """{$RULE,"notificationType":"entity_change","changeType":[],"enabled":true} {"trailing":true}"""
            )
    }

    private val mapper = applicationJsonMapper()

    private fun <T> read(
        json: String,
        type: Class<T>
    ): String =
        try {
            mapper.readValue(json, type).toString()
        } catch (ex: Exception) {
            "fails"
        }

    @Test
    fun `should read a stored outbox entry with its revision, unknown fields and dates in every stored form`() {
        // Given
        val entryType =
            mapper.typeFactory.constructParametricType(OutboxEntry::class.java, CreateUserNotificationEvent::class.java)
        val stored =
            """
            {"_id":"OutboxEntry:key-1","_rev":"4-abc","extra":{"x":1},
             "payload":{"userIdentifier":"user-1","notificationChannelType":"EMAIL","notificationRule":"rule-1",
               "details":{"id":"0b6f0a52-5c1e-4d3c-9d6e-1f7f5b2b8a11","notificationType":"entity_change",
                 "title":"Child updated","body":null,"actionUrl":"/child/1",
                 "context":{"entityType":"Child","entityId":null,"future":true},"created":1790590530.123456789},
               "addedLater":"ignored"},
             "attempts":2,"nextAttemptAt":1790590530,"lastError":null,"createdAt":"2026-09-28T10:15:30.120Z"}
            """.trimIndent()

        // When
        val entry = mapper.readValue<OutboxEntry<CreateUserNotificationEvent>>(stored, entryType)

        // Then
        assertThat(entry.toString()).isEqualTo(
            "OutboxEntry(id=OutboxEntry:key-1, payload=CreateUserNotificationEvent(userIdentifier=user-1, notificationChannelType=EMAIL, notificationRule=rule-1, details=NotificationDetails(id=0b6f0a52-5c1e-4d3c-9d6e-1f7f5b2b8a11, notificationType=ENTITY_CHANGE, title=Child updated, body=null, actionUrl=/child/1, context=EntityNotificationContext(entityType=Child, entityId=null), created=2026-09-28T10:15:30.123456789Z)), attempts=2, nextAttemptAt=2026-09-28T10:15:30Z, lastError=null, createdAt=2026-09-28T10:15:30.120Z)"
        )
    }

    @Test
    fun `should read notification rules as leniently as users write them`() {
        // When
        val outcomes = NOTIFICATION_RULES.map { read(it, NotificationRuleDto::class.java) }

        // Then
        assertThat(outcomes).containsExactly(
            """NotificationRuleDto(label=Rule, notificationType=ENTITY_CHANGE, entityType=Child, changeType=[created], conditions={"name":"Ada"}, enabled=true)""",
            """NotificationRuleDto(label=Rule, notificationType=ENTITY_CHANGE, entityType=Child, changeType=[created], conditions={"name":"Ada"}, enabled=false)""",
            """NotificationRuleDto(label=Rule, notificationType=ENTITY_CHANGE, entityType=Child, changeType=[created], conditions={"name":"Ada"}, enabled=false)""",
            """NotificationRuleDto(label=Rule, notificationType=ENTITY_CHANGE, entityType=Child, changeType=[created, null], conditions={"name":"Ada"}, enabled=true)""",
            "fails",
            """NotificationRuleDto(label=Rule, notificationType=UNKNOWN, entityType=Child, changeType=[], conditions={"name":"Ada"}, enabled=true)""",
            """NotificationRuleDto(label=Rule, notificationType=UNKNOWN, entityType=Child, changeType=[], conditions={"name":"Ada"}, enabled=true)""",
            "fails",
            """NotificationRuleDto(label=Rule, notificationType=ENTITY_CHANGE, entityType=Child, changeType=[], conditions={"name":"Ada"}, enabled=true)""",
            """NotificationRuleDto(label=Rule, notificationType=ENTITY_CHANGE, entityType=Child, changeType=[], conditions={"name":"Ada"}, enabled=true)""",
            "fails",
            "fails",
            "NotificationRuleDto(label=Rule, notificationType=ENTITY_CHANGE, entityType=Child, changeType=[], conditions=null, enabled=true)",
            "fails",
            "fails",
            """NotificationRuleDto(label=Rule, notificationType=ENTITY_CHANGE, entityType=Child, changeType=[], conditions={"name":"Ada"}, enabled=true)"""
        )
    }

    @Test
    fun `should convert notification rules from document trees as the config cache does`() {
        // When
        val outcomes =
            NOTIFICATION_RULES.map {
                try {
                    mapper.convertValue(mapper.readTree(it), NotificationRuleDto::class.java).toString()
                } catch (ex: Exception) {
                    "fails"
                }
            }

        // Then
        assertThat(outcomes).containsExactly(
            """NotificationRuleDto(label=Rule, notificationType=ENTITY_CHANGE, entityType=Child, changeType=[created], conditions={"name":"Ada"}, enabled=true)""",
            """NotificationRuleDto(label=Rule, notificationType=ENTITY_CHANGE, entityType=Child, changeType=[created], conditions={"name":"Ada"}, enabled=false)""",
            """NotificationRuleDto(label=Rule, notificationType=ENTITY_CHANGE, entityType=Child, changeType=[created], conditions={"name":"Ada"}, enabled=false)""",
            """NotificationRuleDto(label=Rule, notificationType=ENTITY_CHANGE, entityType=Child, changeType=[created, null], conditions={"name":"Ada"}, enabled=true)""",
            "fails",
            """NotificationRuleDto(label=Rule, notificationType=UNKNOWN, entityType=Child, changeType=[], conditions={"name":"Ada"}, enabled=true)""",
            """NotificationRuleDto(label=Rule, notificationType=UNKNOWN, entityType=Child, changeType=[], conditions={"name":"Ada"}, enabled=true)""",
            "fails",
            """NotificationRuleDto(label=Rule, notificationType=ENTITY_CHANGE, entityType=Child, changeType=[], conditions={"name":"Ada"}, enabled=true)""",
            """NotificationRuleDto(label=Rule, notificationType=ENTITY_CHANGE, entityType=Child, changeType=[], conditions={"name":"Ada"}, enabled=true)""",
            "fails",
            "fails",
            "NotificationRuleDto(label=Rule, notificationType=ENTITY_CHANGE, entityType=Child, changeType=[], conditions=null, enabled=true)",
            "fails",
            "fails",
            """NotificationRuleDto(label=Rule, notificationType=ENTITY_CHANGE, entityType=Child, changeType=[], conditions={"name":"Ada"}, enabled=true)"""
        )
    }

    @Test
    fun `should read a notification config document with its revision and unknown fields`() {
        // Given
        val document =
            """
            {"_id":"NotificationConfig:user-1","_rev":"2-abc","createdAt":"2026-01-01","notificationRules":[
              {"label":"Rule","notificationType":"entity_change","entityType":"Child","changeType":["created","updated"],
               "conditions":{"${'$'}or":[{"age":{"${'$'}gte":18}},{"name":null}]},"enabled":true,"id":"rule-1"}],
             "channels":{"push":null,"email":""}}
            """.trimIndent()

        // When
        val config = read(document, NotificationConfigDto::class.java)

        // Then
        assertThat(config).isEqualTo(
            """NotificationConfigDto(id=NotificationConfig:user-1, rev=2-abc, notificationRules=[NotificationRuleDto(label=Rule, notificationType=ENTITY_CHANGE, entityType=Child, changeType=[created, updated], conditions={"${'$'}or":[{"age":{"${'$'}gte":18}},{"name":null}]}, enabled=true)], channels=NotificationChannelConfig(push=null, email=null))"""
        )
    }

    @Test
    fun `should read enum values from strings as the skill API does`() {
        // When
        val outcomes =
            listOf("BI-WEEKLY", "ALWAYS", "RARELY", "", "always").map {
                try {
                    mapper.convertValue(it, SkillUsage::class.java).toString()
                } catch (ex: Exception) {
                    "fails"
                }
            }

        // Then
        assertThat(outcomes).containsExactly(
            "BI_WEEKLY",
            "ALWAYS",
            "UNKNOWN",
            "UNKNOWN",
            "UNKNOWN"
        )
    }

    @Test
    fun `should read stored date and time values in their numeric and textual forms`() {
        // When
        val outcomes =
            listOf(
                """{"deviceName":null,"deviceToken":"t","userIdentifier":"u","createdAt":"2026-09-28T12:15:30.5+02:00"}""",
                """{"deviceName":null,"deviceToken":"t","userIdentifier":"u","createdAt":1790590530.5}""",
                """{"deviceName":null,"deviceToken":"t","userIdentifier":"u","createdAt":1790590530123}""",
                """{"deviceName":null,"deviceToken":"t","userIdentifier":"u"}""",
                """{"deviceName":null,"deviceToken":"t","userIdentifier":"u","createdAt":""}"""
            ).map { read(it, UserDeviceEntity::class.java) } +
                listOf(
                    """{"id":"Webhook:1","label":"l","target":{"method":"POST","url":"u"},
                    "authentication":{"type":"API_KEY","iv":"i","data":"d"},"owner":{"creator":"c"},
                    "createdAt":1790590530.123456789}""",
                    """{"id":"Webhook:1","label":"l","target":{"method":"POST","url":"u"},
                    "authentication":{"type":"API_KEY","iv":"i","data":"d"},"owner":{"creator":"c"},
                    "createdAt":"2026-09-28T10:15:30Z"}""",
                    """{"id":"Webhook:1","label":"l","target":{"method":"POST","url":"u"},
                    "authentication":{"type":"API_KEY","iv":"i","data":"d"},"owner":{"creator":"c"}}"""
                ).map { read(it, WebhookEntity::class.java) }

        // Then
        assertThat(outcomes).containsExactly(
            "UserDeviceEntity(deviceName=null, deviceToken=t, userIdentifier=u, createdAt=2026-09-28T10:15:30.500Z)",
            "UserDeviceEntity(deviceName=null, deviceToken=t, userIdentifier=u, createdAt=2026-09-28T10:15:30.500Z)",
            "UserDeviceEntity(deviceName=null, deviceToken=t, userIdentifier=u, createdAt=+58711-07-23T10:22:03Z)",
            "UserDeviceEntity(deviceName=null, deviceToken=t, userIdentifier=u, createdAt=null)",
            "UserDeviceEntity(deviceName=null, deviceToken=t, userIdentifier=u, createdAt=null)",
            "WebhookEntity(id=Webhook:1, label=l, target=WebhookTarget(method=POST, url=u), authentication=WebhookAuthenticationEntity(type=API_KEY, iv=i, data=d), owner=WebhookOwner(creator=c, users=[], groups=[], roles=[]), reportSubscriptions=[], createdAt=2026-09-28T10:15:30.123456789Z)",
            "WebhookEntity(id=Webhook:1, label=l, target=WebhookTarget(method=POST, url=u), authentication=WebhookAuthenticationEntity(type=API_KEY, iv=i, data=d), owner=WebhookOwner(creator=c, users=[], groups=[], roles=[]), reportSubscriptions=[], createdAt=2026-09-28T10:15:30Z)",
            "WebhookEntity(id=Webhook:1, label=l, target=WebhookTarget(method=POST, url=u), authentication=WebhookAuthenticationEntity(type=API_KEY, iv=i, data=d), owner=WebhookOwner(creator=c, users=[], groups=[], roles=[]), reportSubscriptions=[], createdAt=null)"
        )
    }

    @Test
    fun `should read CouchDB documents the reporting module keeps with revisions and unknown fields`() {
        // When
        val outcomes =
            listOf(
                read(
                    """{"_id":"_design/sqlite:config","_rev":"7-abc","language":"sqlite","configVersion":"stored-version",
                    "sql":{"tables":{"Child":{"fields":{"name":{"field":"name","type":"TEXT"}}}},"indexes":null,
                    "options":{"table_name":{"operation":"prefix","field":"_id","separator":":"}}}}""",
                    SqsSchema::class.java
                ),
                read(
                    """{"_id":"Config:CONFIG_ENTITY","_rev":"9-abc","data":{"entity:Child":{"label":"Child",
                    "attributes":{"name":{"dataType":"string","label":"Name"}},"icon":"child"},
                    "entity:School":{"label":null,"attributes":null},"view:child":{"component":"X"}}}""",
                    AppConfigFile::class.java
                ),
                read(
                    """{"_id":"ReportConfig:1","_rev":"1-abc","title":"Report","mode":"sql","transformations":null,
                    "reportDefinition":[{"query":"SELECT 1"},{"groupTitle":"Group","items":[{"query":"SELECT 2"}]}],
                    "created":{"at":"2026-01-01","by":"user"}}""",
                    ReportConfigEntity::class.java
                ),
                read(
                    """{"_id":"ReportConfig:1","_rev":"1-abc","title":"Report"}""",
                    ReportConfigEntity::class.java
                ),
                read(
                    """{"_id":"ReportCalculation:1","_rev":"3-abc","report":{"id":"ReportConfig:1"},
                    "status":"FINISHED_SUCCESS","args":{"from":"2026-01-01"},"_attachments":{"data.json":
                    {"content_type":"application/json","revpos":"2","digest":"md5-abc==","length":12.0,"stub":"true"}}}""",
                    ReportCalculationEntity::class.java
                ),
                read(
                    """{"_id":"ReportCalculation:1","report":{"id":"ReportConfig:1"},"status":"PENDING",
                    "_attachments":{"data.json":{"content_type":"application/json","revpos":null,"digest":"d",
                    "length":1,"stub":true}}}""",
                    ReportCalculationEntity::class.java
                )
            )

        // Then
        assertThat(outcomes).containsExactly(
            "SqsSchema(language=sqlite, sql=SqlObject(tables={Child=TableFields(fields={name=EntityAttributeType(field=name, type=TEXT)})}, indexes=null, options=SqlOptions(tableName=TableName(operation=prefix, field=_id, separator=:))))",
            "AppConfigFile(id=Config:CONFIG_ENTITY, rev=9-abc, data={entity:Child=AppConfigEntry(label=Child, attributes={name=AppConfigAttribute(dataType=string)}), entity:School=AppConfigEntry(label=null, attributes=null), view:child=AppConfigEntry(label=null, attributes=null)})",
            "ReportConfigEntity(id=ReportConfig:1, rev=1-abc, title=Report, mode=sql, transformations=null, reportDefinition=[ReportDefinitionDto(query=SELECT 1, groupTitle=null, items=null), ReportDefinitionDto(query=null, groupTitle=Group, items=[ReportDefinitionDto(query=SELECT 2, groupTitle=null, items=null)])])",
            "fails",
            "ReportCalculationEntity(id=ReportCalculation:1, report=DomainReference(id=ReportConfig:1), status=FINISHED_SUCCESS, errorDetails=null, calculationStarted=null, calculationCompleted=null, args={from=2026-01-01}, attachments={data.json=AttachmentMetaData(contentType=application/json, revpos=2, digest=md5-abc==, length=12, stub=true)}, fromAutomaticChangeDetection=false)",
            "ReportCalculationEntity(id=ReportCalculation:1, report=DomainReference(id=ReportConfig:1), status=PENDING, errorDetails=null, calculationStarted=null, calculationCompleted=null, args={}, attachments={data.json=AttachmentMetaData(contentType=application/json, revpos=0, digest=d, length=1, stub=true)}, fromAutomaticChangeDetection=false)"
        )
    }

    @Test
    fun `should keep the config version stored with the SQS schema rather than derive a new one`() {
        // Given
        val stored =
            """{"_id":"_design/sqlite:config","_rev":"7-abc","language":"sqlite","configVersion":"stored-version",
            "sql":{"tables":{},"indexes":[],"options":{"table_name":{"operation":"prefix","field":"_id","separator":":"}}}}"""

        // When
        val schema = mapper.readValue(stored, SqsSchema::class.java)

        // Then
        assertThat(schema.configVersion).isEqualTo("stored-version")
    }

    @Test
    fun `should read CouchDB responses and cursors`() {
        // When
        val outcomes =
            listOf(
                read("""{"ok":true,"id":"Child:1","rev":"2-abc"}""", DocSuccess::class.java),
                read("""{"ok":true,"id":"Child:1","rev":"2-abc"} {"trailing":true}""", DocSuccess::class.java),
                read(
                    """{"_id":"SyncEntry:app:notification","_rev":"5-abc","database":"app","latestRef":"12-g1AAAA",
                    "consumer":"notification","updatedAt":"x"}""",
                    SyncEntryDocument::class.java
                ),
                read(
                    """{"_id":"SyncEntry:app","_rev":"5-abc","database":"app","latestRef":"12-g1AAAA"}""",
                    SyncEntryDocument::class.java
                ),
                read(
                    """{"results":[{"seq":"13-g1","id":"Child:1","changes":[{"rev":"2-abc"}],
                    "doc":{"_id":"Child:1","_rev":"2-abc","name":"Ada","age":3.5,"tags":["a",null]}},
                    {"seq":"14-g1","id":"Child:2","changes":[{"rev":"3-abc"}],"deleted":true,"doc":null}],
                    "last_seq":"14-g1","pending":0}""",
                    CouchDbChangesResponse::class.java
                ),
                read(
                    """{"access_token":"token-1","expires_in":300,"token_type":"Bearer"}""",
                    KeycloakTokenResponse::class.java
                ),
                read("""{"access_token":"token-1","access_token":"token-2"}""", KeycloakTokenResponse::class.java)
            )

        // Then
        assertThat(outcomes).containsExactly(
            "DocSuccess(ok=true, id=Child:1, rev=2-abc)",
            "DocSuccess(ok=true, id=Child:1, rev=2-abc)",
            "SyncEntryDocument(database=app, latestRef=12-g1AAAA, consumer=notification, rev=5-abc)",
            "SyncEntryDocument(database=app, latestRef=12-g1AAAA, consumer=null, rev=5-abc)",
            """CouchDbChangesResponse(lastSeq=14-g1, results=[CouchDbChangeResult(id=Child:1, changes=[CouchDbChange(rev=2-abc)], seq=13-g1, doc={"_id":"Child:1","_rev":"2-abc","name":"Ada","age":3.5,"tags":["a",null]}, deleted=false), CouchDbChangeResult(id=Child:2, changes=[CouchDbChange(rev=3-abc)], seq=14-g1, doc=null, deleted=true)], pending=0)""",
            "KeycloakTokenResponse(accessToken=token-1)",
            "KeycloakTokenResponse(accessToken=token-2)"
        )
    }

    @Test
    fun `should read permission check answers with missing, extra and unexpected values`() {
        // Given
        val resultsType =
            mapper.typeFactory.constructMapType(Map::class.java, String::class.java, PermissionCheckResult::class.java)
        val answer =
            """{"user-1":{"permitted":true},"user-2":{"permitted":false,"reason":"x"},"user-3":{"error":"NOT_FOUND"},
            "user-4":{},"user-5":{"permitted":null}}"""

        // When
        val results = mapper.readValue<Map<String, PermissionCheckResult>>(answer, resultsType)

        // Then
        assertThat(results.toString()).isEqualTo(
            "{user-1=PermissionCheckResult(permitted=true, error=null), user-2=PermissionCheckResult(permitted=false, error=null), user-3=PermissionCheckResult(permitted=null, error=NOT_FOUND), user-4=PermissionCheckResult(permitted=null, error=null), user-5=PermissionCheckResult(permitted=null, error=null)}"
        )
    }

    @Test
    fun `should convert a document tree into a map with the number types change handlers compare`() {
        // Given
        val document =
            mapper.readTree(
                """{"int":1,"long":12345678901,"big":123456789012345678901234567890,"double":1.5,"exponent":1e2,
                "bool":true,"nothing":null,"text":"x","list":[1,"a",null],"map":{"k":2}}"""
            )

        // When
        val map = mapper.convertValue(document, Map::class.java)

        // Then
        assertThat(map.toString()).isEqualTo(
            "{int=1, long=12345678901, big=123456789012345678901234567890, double=1.5, exponent=100.0, bool=true, nothing=null, text=x, list=[1, a, null], map={k=2}}"
        )
        assertThat(map.values.map { it?.javaClass?.simpleName }).containsExactly(
            "Integer",
            "Long",
            "BigInteger",
            "Double",
            "Double",
            "Boolean",
            null,
            "String",
            "ArrayList",
            "LinkedHashMap"
        )
    }
}
