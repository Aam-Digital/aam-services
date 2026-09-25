package com.aamdigital.aambackendservice.common.couchdb.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.util.LinkedMultiValueMap
import kotlin.reflect.KClass

/** Returns query params for `_all_docs` scoped to documents with the given key prefix. */
fun getQueryParamsAllDocs(key: String): LinkedMultiValueMap<String, String> {
    val queryParams = LinkedMultiValueMap<String, String>()
    queryParams.add("include_docs", "true")
    queryParams.add("startkey", "\"$key:\"")
    queryParams.add("endkey", "\"$key:\\ufff0\"")
    return queryParams
}

/** Creates an empty [LinkedMultiValueMap] for CouchDB query parameters. */
fun getEmptyQueryParams() = LinkedMultiValueMap<String, String>()

/**
 * Database holding backend-internal state that is not part of the application's user-facing data:
 * the change-detection cursor, push device registrations and third-party-auth redirect bindings.
 *
 * Deliberately *not* the `app` database: `app` is replicated to clients through
 * replication-backend and is the database change detection polls, so keeping internal state there
 * would both expose it and - for the cursor - make change detection retrigger itself on every
 * write.
 */
const val BACKEND_STATE_DATABASE = "aam-backend-state"

/**
 * Fetches every document in [database] whose id starts with `<prefix>:`, deserialized to [kClass].
 *
 * The document-id prefix is what stands in for a table here, so a collection scan is a prefix scan
 * over `_all_docs`.
 */
fun <T : Any> fetchAllDocumentsByPrefix(
    couchDbClient: CouchDbClient,
    objectMapper: ObjectMapper,
    database: String,
    prefix: String,
    kClass: KClass<T>
): List<T> {
    val response =
        couchDbClient.getDatabaseDocument(
            database = database,
            documentId = "_all_docs",
            queryParams = getQueryParamsAllDocs(prefix),
            kClass = ObjectNode::class
        )

    val rows = response.get("rows") ?: return emptyList()

    return rows
        .mapNotNull { row -> row.get("doc") }
        .map { doc -> objectMapper.convertValue(doc, kClass.java) }
}
