package com.aamdigital.aambackendservice.common.couchdb.core

import org.springframework.util.LinkedMultiValueMap

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
