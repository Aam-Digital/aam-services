package com.aamdigital.aambackendservice.reporting.report.sqs

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.domain.EntityAttribute
import com.aamdigital.aambackendservice.common.domain.EntityAttributeType
import com.aamdigital.aambackendservice.common.domain.EntityConfig
import com.aamdigital.aambackendservice.common.domain.EntityType
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import java.security.MessageDigest

data class AppConfigAttribute(
    val dataType: String,
)

data class AppConfigEntry(
    val label: String?,
    val attributes: Map<String, AppConfigAttribute>?,
)

data class AppConfigFile(
    @JsonProperty("_id")
    val id: String,
    @JsonProperty("_rev")
    val rev: String,
    val data: Map<String, AppConfigEntry>?,
)

data class TableFields(
    val fields: Map<String, EntityAttributeType>
)

data class TableName(
    val operation: String = "prefix",
    val field: String,
    val separator: String,
)

data class SqlObject(
    val tables: Map<String, TableFields>,
    val indexes: List<String>?,
    val options: SqlOptions,
)

data class SqlOptions(
    @JsonProperty("table_name")
    val tableName: TableName
)

data class SqsSchema(
    val language: String = "sqlite",
    val sql: SqlObject,
) {
    var configVersion: String

    init {
        configVersion = generateConfigVersion()
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun generateConfigVersion(): String {
        val md = MessageDigest.getInstance("SHA-256")
        val input = jacksonObjectMapper().writeValueAsString(sql).toByteArray()
        val bytes = md.digest(input)
        return bytes.toHexString()
    }
}

@Service
class SqsSchemaService(
    private val couchDbClient: CouchDbClient,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val FILENAME_CONFIG_ENTITY = "Config:CONFIG_ENTITY";
        private const val SCHEMA_PATH = "_design/sqlite:config"
        private const val TARGET_DATABASE = "app"
    }

    fun getSchemaPath(): String = "/$TARGET_DATABASE/$SCHEMA_PATH"

    fun updateSchema() {
        val config = couchDbClient.getDatabaseDocument(
            database = TARGET_DATABASE,
            documentId = FILENAME_CONFIG_ENTITY,
            queryParams = LinkedMultiValueMap(),
            kClass = AppConfigFile::class
        )

        val entities: List<EntityType> = config.data.orEmpty().keys
            .filter {
                it.startsWith("entity:")
            }
            .map {
                val entityType: AppConfigEntry = config.data.orEmpty().getValue(it)
                parseEntityConfig(it, entityType)
            }

        val entityConfig = EntityConfig(config.rev, entities)

        val currentSqsSchema = try {
            couchDbClient.getDatabaseDocument(
                database = TARGET_DATABASE,
                documentId = SCHEMA_PATH,
                queryParams = LinkedMultiValueMap(),
                kClass = SqsSchema::class
            )
        } catch (ex: Exception) {
            logger.warn("[SqsSchemaService] No current SQS Schema found. Creating it.", ex)
            null
        }

        val newSqsSchema: SqsSchema = mapToSqsSchema(entityConfig)

        if (currentSqsSchema?.configVersion == newSqsSchema.configVersion) {
            return
        }

        couchDbClient.putDatabaseDocument(
            database = TARGET_DATABASE,
            documentId = SCHEMA_PATH,
            body = newSqsSchema,
        )
    }

    private fun mapToSqsSchema(entityConfig: EntityConfig): SqsSchema {
        val tables = entityConfig.entities.map { entityType ->
            val attributes = entityType.attributes
                .filter {
                    it.type.type != "file"
                }
                .map {
                    EntityAttribute(
                        it.name,
                        EntityAttributeType(
                            it.name,
                            type = mapConfigDataTypeToSqsDataType(it.type.type)
                        )
                    )
                }
                .plus(getDefaultEntityAttributes())

            Pair(entityType.label, attributes.associate {
                Pair(it.name, it.type)
            })
        }.associate {
            Pair(it.first, TableFields(it.second))
        }

        return SqsSchema(
            sql = SqlObject(
                tables = tables,
                options = SqlOptions(
                    TableName(
                        field = "_id",
                        separator = ":"
                    )
                ),
                indexes = emptyList(),
            )
        )
    }

    private fun mapConfigDataTypeToSqsDataType(dataType: String): String = when (dataType) {
        "number",
        "integer",
        "boolean" -> {
            "INTEGER"
        }

        else -> "TEXT"
    }

    private fun getDefaultEntityAttributes(): List<EntityAttribute> = listOf(
        EntityAttribute(
            "_id", EntityAttributeType(
                field = "_id",
                type = "TEXT"
            )
        ),
        EntityAttribute(
            "_rev", EntityAttributeType(
                field = "_rev",
                type = "TEXT"
            )
        ),
        EntityAttribute(
            "_attachments", EntityAttributeType(
                field = "_attachments",
                type = "TEXT"
            )
        ),
        EntityAttribute(
            "created_at", EntityAttributeType(
                field = "created.at",
                type = "DATE"
            )
        ),
        EntityAttribute(
            "created_by", EntityAttributeType(
                field = "created.by",
                type = "TEXT"
            )
        ),
        EntityAttribute(
            "updated_at", EntityAttributeType(
                field = "updated.at",
                type = "DATE"
            )
        ),
        EntityAttribute(
            "updated_by", EntityAttributeType(
                field = "updated.by",
                type = "TEXT"
            )
        ),
        EntityAttribute(
            "inactive", EntityAttributeType(
                field = "inactive",
                type = "INTEGER"
            )
        ),
        EntityAttribute(
            "anonymized", EntityAttributeType(
                field = "anonymized",
                type = "INTEGER"
            )
        ),
    )


    private fun parseEntityConfig(entityKey: String, config: AppConfigEntry): EntityType {
        return EntityType(
            label = entityKey.split(":")[1],
            attributes = config.attributes.orEmpty().map {
                EntityAttribute(
                    name = it.key,
                    type = EntityAttributeType(
                        field = it.key,
                        type = it.value.dataType
                    )
                )
            }
        )
    }

}
