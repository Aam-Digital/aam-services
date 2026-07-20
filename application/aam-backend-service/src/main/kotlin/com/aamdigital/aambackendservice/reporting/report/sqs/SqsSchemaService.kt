package com.aamdigital.aambackendservice.reporting.report.sqs

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.domain.EntityAttribute
import com.aamdigital.aambackendservice.common.domain.EntityAttributeType
import com.aamdigital.aambackendservice.common.domain.EntityConfig
import com.aamdigital.aambackendservice.common.domain.EntityType
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.util.LinkedMultiValueMap
import java.security.MessageDigest

data class AppConfigAttribute(
    val dataType: String
)

data class AppConfigEntry(
    val label: String?,
    val attributes: Map<String, AppConfigAttribute>?
)

data class AppConfigFile(
    @JsonProperty("_id")
    val id: String,
    @JsonProperty("_rev")
    val rev: String,
    val data: Map<String, AppConfigEntry>?
)

data class TableFields(
    val fields: Map<String, EntityAttributeType>
)

data class TableName(
    val operation: String = "prefix",
    val field: String,
    val separator: String
)

data class SqlObject(
    val tables: Map<String, TableFields>,
    val indexes: List<String>?,
    val options: SqlOptions
)

data class SqlOptions(
    @JsonProperty("table_name")
    val tableName: TableName
)

data class SqsSchema(
    val language: String = "sqlite",
    val sql: SqlObject
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

class SqsSchemaService(
    private val couchDbClient: CouchDbClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val FILENAME_CONFIG_ENTITY = "Config:CONFIG_ENTITY"
        private const val SCHEMA_PATH = "_design/sqlite:config"
        private const val TARGET_DATABASE = "app"

        /**
         * Name of the hard-wired table exposing raw ConfigurableEnum documents
         * (one row per enum, options as a JSON array in the "values" column).
         */
        const val CONFIGURABLE_ENUM_TABLE = "ConfigurableEnum"

        /**
         * One row per enum option, so report queries can translate stored option ids
         * into human-readable labels with a plain JOIN instead of json_each boilerplate:
         *
         * SELECT c.name, COALESCE(g.label, c.gender) AS gender
         * FROM Child c
         * LEFT JOIN ConfigurableEnumOption g
         *   ON g.enum_id = 'genders' AND g.option_id = c.gender
         *
         * SQS executes any statement listed in sql.indexes, which lets us ship this view
         * as part of the schema definition.
         */
        const val CONFIGURABLE_ENUM_OPTION_VIEW =
            "CREATE VIEW IF NOT EXISTS ConfigurableEnumOption AS " +
                "SELECT REPLACE(ec._id, 'ConfigurableEnum:', '') AS enum_id, " +
                "json_extract(o.value, '$.id') AS option_id, " +
                "json_extract(o.value, '$.label') AS label " +
                "FROM ConfigurableEnum ec, json_each(ec.\"values\") o"
    }

    fun getSchemaPath(): String = "/$TARGET_DATABASE/$SCHEMA_PATH"

    fun updateSchema() {
        val config =
            couchDbClient.getDatabaseDocument(
                database = TARGET_DATABASE,
                documentId = FILENAME_CONFIG_ENTITY,
                queryParams = LinkedMultiValueMap(),
                kClass = AppConfigFile::class
            )

        val entities: List<EntityType> =
            config.data
                .orEmpty()
                .keys
                .filter {
                    it.startsWith("entity:")
                }.map {
                    val entityType: AppConfigEntry = config.data.orEmpty().getValue(it)
                    parseEntityConfig(it, entityType)
                }

        val entityConfig = EntityConfig(config.rev, entities)

        val currentSqsSchema =
            try {
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
            body = newSqsSchema
        )
    }

    private fun mapToSqsSchema(entityConfig: EntityConfig): SqsSchema {
        val tables =
            entityConfig.entities
                .map { entityType ->
                    val attributes =
                        entityType.attributes
                            .filter {
                                it.type.type != "file"
                            }.map {
                                EntityAttribute(
                                    it.name,
                                    EntityAttributeType(
                                        it.name,
                                        type = mapConfigDataTypeToSqsDataType(it.type.type)
                                    )
                                )
                            }.plus(getDefaultEntityAttributes())

                    Pair(
                        entityType.label,
                        attributes.associate {
                            Pair(it.name, it.type)
                        }
                    )
                }.associate {
                    Pair(it.first, TableFields(it.second))
                }
                // hard-wired last, so it wins over a manually configured entity:ConfigurableEnum
                // (the old workaround that required adding this entity to the config document)
                .plus(getConfigurableEnumTable())

        return SqsSchema(
            sql =
                SqlObject(
                    tables = tables,
                    options =
                        SqlOptions(
                            TableName(
                                field = "_id",
                                separator = ":"
                            )
                        ),
                    indexes = listOf(CONFIGURABLE_ENUM_OPTION_VIEW)
                )
        )
    }

    /**
     * ConfigurableEnum documents are defined in code (not in Config:CONFIG_ENTITY),
     * so their table is hard-wired here to make enum options queryable at all times.
     */
    private fun getConfigurableEnumTable(): Pair<String, TableFields> =
        Pair(
            CONFIGURABLE_ENUM_TABLE,
            TableFields(
                mapOf(
                    "_id" to EntityAttributeType(field = "_id", type = "TEXT"),
                    "values" to EntityAttributeType(field = "values", type = "TEXT")
                )
            )
        )

    private fun mapConfigDataTypeToSqsDataType(dataType: String): String =
        when (dataType) {
            "number",
            "integer",
            "boolean" -> {
                "INTEGER"
            }

            else -> "TEXT"
        }

    private fun getDefaultEntityAttributes(): List<EntityAttribute> =
        listOf(
            EntityAttribute(
                "_id",
                EntityAttributeType(
                    field = "_id",
                    type = "TEXT"
                )
            ),
            EntityAttribute(
                "_rev",
                EntityAttributeType(
                    field = "_rev",
                    type = "TEXT"
                )
            ),
            EntityAttribute(
                "_attachments",
                EntityAttributeType(
                    field = "_attachments",
                    type = "TEXT"
                )
            ),
            // "_" prefix marks columns that are derived from internal metadata rather than
            // configured entity fields, and avoids clashes with custom fields of the same name
            EntityAttribute(
                "_created_at",
                EntityAttributeType(
                    field = "created.at",
                    type = "DATE"
                )
            ),
            EntityAttribute(
                "_created_by",
                EntityAttributeType(
                    field = "created.by",
                    type = "TEXT"
                )
            ),
            EntityAttribute(
                "_updated_at",
                EntityAttributeType(
                    field = "updated.at",
                    type = "DATE"
                )
            ),
            EntityAttribute(
                "_updated_by",
                EntityAttributeType(
                    field = "updated.by",
                    type = "TEXT"
                )
            ),
            EntityAttribute(
                "inactive",
                EntityAttributeType(
                    field = "inactive",
                    type = "INTEGER"
                )
            ),
            EntityAttribute(
                "anonymized",
                EntityAttributeType(
                    field = "anonymized",
                    type = "INTEGER"
                )
            )
        )

    private fun parseEntityConfig(
        entityKey: String,
        config: AppConfigEntry
    ): EntityType =
        EntityType(
            label = entityKey.split(":")[1],
            attributes =
                config.attributes.orEmpty().map {
                    EntityAttribute(
                        name = it.key,
                        type =
                            EntityAttributeType(
                                field = it.key,
                                type = it.value.dataType
                            )
                    )
                }
        )
}
