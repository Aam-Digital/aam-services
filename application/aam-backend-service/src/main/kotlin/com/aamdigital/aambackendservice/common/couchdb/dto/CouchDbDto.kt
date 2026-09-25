package com.aamdigital.aambackendservice.common.couchdb.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.node.ObjectNode

/** A single revision reference (leaf) within a CouchDB changes result. */
data class CouchDbChange(
    val rev: String
)

/** CouchDB success response for write operations (PUT, DELETE). */
data class DocSuccess(
    val ok: Boolean,
    val id: String,
    val rev: String
)

/** Metadata for a CouchDB document attachment (returned as part of the document JSON). */
data class AttachmentMetaData(
    @JsonProperty("content_type")
    val contentType: String,
    val revpos: Int,
    val digest: String,
    val length: Long,
    val stub: Boolean
)

/**
 * Response from CouchDB `_find` (Mango query), containing the matched documents and the
 * [bookmark] to pass for the next page of results.
 */
data class FindResponse<T>(
    val docs: List<T>,
    val bookmark: String? = null
)

/**
 * A single result entry from a CouchDB changes feed,
 * indicating one doc has changed.
 *
 * see https://docs.couchdb.org/en/stable/api/database/changes.html
 */
data class CouchDbChangeResult(
    /** _id of a doc with changes */
    val id: String,
    /** List of document’s leaves with single field rev. */
    val changes: List<CouchDbChange>,
    val seq: String,
    val doc: ObjectNode?,
    val deleted: Boolean? = false
)

/**
 * Response from the CouchDB changes endpoint, listing database docs that have changed
 * since the given last change (last_seq).
 *
 * see https://docs.couchdb.org/en/stable/api/database/changes.html
 */
data class CouchDbChangesResponse(
    @JsonProperty("last_seq")
    val lastSeq: String,
    val results: List<CouchDbChangeResult>,
    val pending: Int
)
