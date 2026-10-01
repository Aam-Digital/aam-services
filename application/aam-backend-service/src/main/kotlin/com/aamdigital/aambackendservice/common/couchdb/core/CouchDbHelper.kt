package com.aamdigital.aambackendservice.common.couchdb.core

import com.aamdigital.aambackendservice.common.couchdb.core.DefaultCouchDbClient.DefaultCouchDbClientErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import org.slf4j.LoggerFactory
import org.springframework.util.LinkedMultiValueMap

private val logger = LoggerFactory.getLogger(CouchDbClient::class.java)

/** Creates an empty [LinkedMultiValueMap] for CouchDB query parameters. */
fun getEmptyQueryParams() = LinkedMultiValueMap<String, String>()

/** Whether [documentId] exists in [database], asked with a `HEAD` request instead of reading it. */
fun CouchDbClient.documentExists(
    database: String,
    documentId: String
): Boolean = !headDatabaseDocument(database = database, documentId = documentId).eTag.isNullOrBlank()

/**
 * Runs [write], and if CouchDB answers that [database] does not exist, creates it and runs [write]
 * once more.
 *
 * For a database that comes into being with its first document (one per user, say) or that may be
 * dropped while the service runs. While the database exists this costs nothing beyond the write,
 * which checking for the database before every write would not.
 *
 * [write] must write to [database] only. Do not wrap a read in this, since a missing database has
 * nothing to find and a read should treat it as empty, nor a delete, which must not bring back a
 * database that was dropped.
 */
fun <T> CouchDbClient.creatingDatabaseIfMissing(
    database: String,
    write: () -> T
): T =
    try {
        write()
    } catch (ex: ExternalSystemException) {
        if (ex.code != DefaultCouchDbClientErrorCode.DATABASE_NOT_FOUND) throw ex

        logger.info("Database {} does not exist, creating it for the write that needs it", database)
        createDatabase(database)
        write()
    }

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
