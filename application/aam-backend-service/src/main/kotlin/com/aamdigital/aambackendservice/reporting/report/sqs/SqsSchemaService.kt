package com.aamdigital.aambackendservice.reporting.report.sqs

import com.aamdigital.aambackendservice.couchdb.core.CouchDbStorage
import com.aamdigital.aambackendservice.crypto.core.CryptoService
import com.aamdigital.aambackendservice.domain.EntityAttribute
import com.aamdigital.aambackendservice.domain.EntityConfig
import com.aamdigital.aambackendservice.domain.EntityType
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import reactor.core.publisher.Mono
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
    val fields: Map<String, String>
)

data class TableName(
    val operation: String = "prefix",
    val field: String,
    val separator: String,
)

data class SqlObject(
    val tables: Map<String, TableFields>,
    val indexes: List<String>,
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
    private val couchDbStorage: CouchDbStorage,
    private val cryptoService: CryptoService,
) {

    companion object {
        private const val FILENAME_CONFIG_ENTITY = "Config:CONFIG_ENTITY";
        private const val SCHEMA_PATH = "_design/sqlite:config"
        private const val TARGET_DATABASE = "app"
    }


    fun getSchemaPath(): String = "/$TARGET_DATABASE/$SCHEMA_PATH"

    fun updateSchema(): Mono<Unit> {
        return Mono.zip(
            couchDbStorage.getDatabaseDocument(
                database = TARGET_DATABASE,
                documentId = FILENAME_CONFIG_ENTITY,
                queryParams = LinkedMultiValueMap(),
                kClass = AppConfigFile::class
            )
                .map { config ->
                    val entities: List<EntityType> = config.data.orEmpty().keys
                        .filter {
                            it.startsWith("entity:")
                        }
                        .map {
                            val entityType: AppConfigEntry = config.data.orEmpty().getValue(it)
                            parseEntityConfig(it, entityType)
                        }

                    EntityConfig(config.rev, entities)
                },
            couchDbStorage.getDatabaseDocument(
                database = TARGET_DATABASE,
                documentId = SCHEMA_PATH,
                queryParams = LinkedMultiValueMap(),
                kClass = SqsSchema::class
            )
        )
            .flatMap {
                val entityConfig = it.t1
                val currentSqsSchema = it.t2
                val newSqsSchema: SqsSchema = mapToSqsSchema(entityConfig)

                if (currentSqsSchema.configVersion == newSqsSchema.configVersion) {
                    return@flatMap Mono.just(Unit)
                }

                couchDbStorage.putDatabaseDocument(
                    database = TARGET_DATABASE,
                    documentId = SCHEMA_PATH,
                    body = newSqsSchema,
                )
                    .flatMap {
                        Mono.just(Unit)
                    }
            }
    }

    private fun mapToSqsSchema(entityConfig: EntityConfig): SqsSchema {
        val tables = entityConfig.entities.map { entityType ->
            val attributes = entityType.attributes
                .filter {
                    it.type != "file"
                }
                .map {
                    EntityAttribute(
                        it.name,
                        mapConfigDataTypeToSqsDataType(it.type)
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
        EntityAttribute("_id", "TEXT"),
        EntityAttribute("_rev", "TEXT"),
        EntityAttribute("created", "TEXT"),
        EntityAttribute("updated", "TEXT"),
        EntityAttribute("inactive", "INTEGER"),
        EntityAttribute("anonymized", "INTEGER"),
    )


    private fun parseEntityConfig(entityKey: String, config: AppConfigEntry): EntityType {
        return EntityType(
            label = config.label ?: entityKey.split(":")[1],
            attributes = config.attributes.orEmpty().map {
                EntityAttribute(
                    name = it.key,
                    type = it.value.dataType
                )
            }
        )
    }

}
