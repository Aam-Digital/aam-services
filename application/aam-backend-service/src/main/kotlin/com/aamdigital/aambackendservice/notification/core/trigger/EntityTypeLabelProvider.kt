package com.aamdigital.aambackendservice.notification.core.trigger

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.domain.TranslatableText
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory

/** Human-readable label of an entity type, as configured for the frontend. */
fun interface EntityTypeLabelProvider {
    /** @return the configured label, or null if none is configured or it cannot be read */
    fun labelFor(entityType: String): String?
}

/**
 * Reads the entity type's `label` from the frontend's entity config (`Config:CONFIG_ENTITY`).
 *
 * Built-in entity types often have no `entity:` entry in that config, so a missing label is the
 * normal case, not an error. The config is read on every call: a label is only looked up for
 * rules without a label of their own, and reading on demand keeps it in step with config changes.
 */
class CouchDbEntityTypeLabelProvider(
    private val couchDbClient: CouchDbClient
) : EntityTypeLabelProvider {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun labelFor(entityType: String): String? =
        try {
            val config =
                couchDbClient.getDatabaseDocument(
                    database = DATABASE,
                    documentId = CONFIG_ENTITY_DOCUMENT_ID,
                    kClass = JsonNode::class
                )
            TranslatableText
                .resolve(config.path("data").path("entity:$entityType").path("label"))
                ?.takeIf { it.isNotBlank() }
        } catch (ex: NotFoundException) {
            null
        } catch (ex: Exception) {
            // no label is a graceful degradation, the notification falls back to the entity type id
            logger.warn("Could not read the label of entity type {} from the entity config", entityType, ex)
            null
        }

    companion object {
        private const val DATABASE = "app"
        private const val CONFIG_ENTITY_DOCUMENT_ID = "Config:CONFIG_ENTITY"
    }
}
