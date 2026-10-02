package com.aamdigital.aambackendservice.common.changes

/**
 * Reads the previous revision of a changed document.
 *
 * Fetching it costs a CouchDB round trip, and it is polled separately for every
 * [DocumentChangeHandler], so paying for it eagerly means paying once per change *per consumer* -
 * including for the consumers that never look at it. [DocumentChangeEvent.previousVersion] goes
 * through this instead, so the round trip happens only if a handler actually asks.
 */
fun interface PreviousDocumentVersionLoader {
    fun load(): Map<*, *>

    companion object {
        /** There is no previous revision to read: a deletion, or an event built from values. */
        val NONE: PreviousDocumentVersionLoader = PreviousDocumentVersionLoader { emptyMap<String, Any>() }
    }
}

/**
 * Domain event representing a single document change in a CouchDB database.
 * Carries both the current and previous version of the document for consumers
 * to determine what changed.
 *
 * [currentVersion] comes free with the change itself (the feed is read with `include_docs`), while
 * [previousVersion] is loaded on first access - see [PreviousDocumentVersionLoader].
 */
data class DocumentChangeEvent(
    val database: String,
    val documentId: String,
    val rev: String,
    val currentVersion: Map<*, *>,
    val deleted: Boolean,
    private val previousVersionLoader: PreviousDocumentVersionLoader = PreviousDocumentVersionLoader.NONE
) {
    /** Builds an event whose previous version is already known, rather than loaded on demand. */
    constructor(
        database: String,
        documentId: String,
        rev: String,
        currentVersion: Map<*, *>,
        previousVersion: Map<*, *>,
        deleted: Boolean
    ) : this(
        database = database,
        documentId = documentId,
        rev = rev,
        currentVersion = currentVersion,
        deleted = deleted,
        previousVersionLoader = PreviousDocumentVersionLoader { previousVersion }
    )

    /**
     * The document as it was before this change, or an empty map when there is none - a creation,
     * a deletion, or a previous revision CouchDB has already compacted away.
     *
     * Loaded on first access and then kept, so a handler may read it repeatedly for one event
     * without repeating the round trip, and a handler that never reads it costs nothing.
     */
    val previousVersion: Map<*, *> by lazy { previousVersionLoader.load() }

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
     * e.g. when the previous revision has been purged - and which reading here would defeat the
     * point of loading it lazily.
     */
    val changeType: String
        get() =
            when {
                deleted -> "deleted"
                (rev.substringBefore("-").toIntOrNull() ?: 1) > 1 -> "updated"
                else -> "created"
            }
}
