package com.aamdigital.aambackendservice.common.changes

/**
 * Domain event representing a single document change in a CouchDB database.
 * Carries both the current and previous version of the document for consumers
 * to determine what changed.
 */
data class DocumentChangeEvent(
    val database: String,
    val documentId: String,
    val rev: String,
    val currentVersion: Map<*, *>,
    val previousVersion: Map<*, *>,
    val deleted: Boolean
) {
    /**
     * The entity type of the changed document, by the convention that a document id is
     * `<EntityType>:<id>` (e.g. `Child` for `Child:1`).
     */
    val entityType: String
        get() = documentId.substringBefore(":")

    /**
     * `"deleted"`, `"updated"` or `"created"`, the vocabulary notification rules use.
     *
     * Created and updated are told apart by the revision's generation (`<generation>-<hash>`, where
     * 1 is the first revision) rather than by [previousVersion], which can be empty for an update,
     * e.g. when the previous revision has been purged.
     */
    val changeType: String
        get() =
            when {
                deleted -> "deleted"
                (rev.substringBefore("-").toIntOrNull() ?: 1) > 1 -> "updated"
                else -> "created"
            }
}
