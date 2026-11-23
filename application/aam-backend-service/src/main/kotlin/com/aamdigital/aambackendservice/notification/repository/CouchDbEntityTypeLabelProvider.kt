package com.aamdigital.aambackendservice.notification.repository

import com.aamdigital.aambackendservice.common.cache.LazySnapshot
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.domain.TranslatableText
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.notification.core.trigger.EntityTypeLabelProvider
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory

/**
 * Reads the entity types' `label` from the frontend's entity config (`Config:CONFIG_ENTITY`).
 *
 * The labels are kept in memory, because they are looked up during change handling, which has to
 * answer from memory. They are loaded on first use and loaded again after [invalidate], which the
 * change handler calls whenever the entity config changes.
 *
 * Built-in entity types often have no `entity:` entry in that config, so a missing label is the
 * normal case, not an error.
 */
class CouchDbEntityTypeLabelProvider(
    private val couchDbClient: CouchDbClient
) : EntityTypeLabelProvider {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Labels by entity type, e.g. `Child` to `Participant`. */
    private val labels = LazySnapshot { loadLabels() }

    override fun labelFor(entityType: String): String? =
        try {
            labels.get()[entityType]
        } catch (ex: Exception) {
            // no label is a graceful degradation, the notification falls back to the entity type id;
            // nothing is cached after a failed load, so the next lookup tries again
            logger.warn("Could not read the entity type labels from the entity config", ex)
            null
        }

    override fun invalidate() = labels.invalidate()

    private fun loadLabels(): Map<String, String> {
        val config =
            try {
                couchDbClient.getDatabaseDocument(
                    database = DATABASE,
                    documentId = CONFIG_ENTITY_DOCUMENT_ID,
                    kClass = JsonNode::class
                )
            } catch (
                @Suppress("SwallowedException") ex: NotFoundException
            ) {
                return emptyMap()
            }

        return config
            .path("data")
            .properties()
            .filter { it.key.startsWith(ENTITY_PREFIX) }
            .mapNotNull { (key, entityConfig) ->
                TranslatableText
                    .resolve(entityConfig.path("label"))
                    ?.takeIf { it.isNotBlank() }
                    ?.let { key.removePrefix(ENTITY_PREFIX) to it }
            }.toMap()
    }

    companion object {
        const val CONFIG_ENTITY_DOCUMENT_ID = "Config:CONFIG_ENTITY"
        private const val DATABASE = "app"
        private const val ENTITY_PREFIX = "entity:"
    }
}
