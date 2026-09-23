package com.aamdigital.aambackendservice.common.couchdb.core

import com.aamdigital.aambackendservice.common.couchdb.dto.CouchDbChangesResponse
import com.aamdigital.aambackendservice.common.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.common.couchdb.dto.FindResponse
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import org.springframework.http.HttpHeaders
import org.springframework.util.MultiValueMap
import java.io.InterruptedIOException
import java.util.*
import kotlin.reflect.KClass

/**
 * HTTP client abstraction for CouchDB operations.
 * Provides methods for CRUD, querying (`_find`), change feeds (`_changes`),
 * and revision history. See [DefaultCouchDbClient] for the implementation.
 */
interface CouchDbClient {
    fun allDatabases(): List<String>

    fun getDatabaseChanges(
        database: String,
        queryParams: MultiValueMap<String, String>
    ): CouchDbChangesResponse

    fun <T : Any> findDatabaseDocuments(
        database: String,
        body: Map<String, Any>,
        queryParams: MultiValueMap<String, String> = getEmptyQueryParams(),
        kClass: KClass<T>
    ): FindResponse<T>

    fun headDatabaseDocument(
        database: String,
        documentId: String
    ): HttpHeaders

    @Throws(
        NotFoundException::class,
        ExternalSystemException::class,
        InterruptedIOException::class
    )
    fun <T : Any> getDatabaseDocument(
        database: String,
        documentId: String,
        queryParams: MultiValueMap<String, String> = getEmptyQueryParams(),
        kClass: KClass<T>
    ): T

    /**
     * Writes [body] over whatever revision the document is at right now. The revision is looked up
     * immediately before the write, so this does *not* protect a read-modify-write: a change made
     * since the caller read the document is silently overwritten. Use
     * [putDatabaseDocumentAtRevision] for that.
     */
    fun putDatabaseDocument(
        database: String,
        documentId: String,
        body: Any
    ): DocSuccess

    /**
     * Writes [body] only if the document is still at [expectedRev], or - with `null` - only if it
     * does not exist yet. A concurrent write since [expectedRev] was read makes CouchDB answer
     * 409, which is thrown as an [ExternalSystemException].
     */
    @Throws(ExternalSystemException::class)
    fun putDatabaseDocumentAtRevision(
        database: String,
        documentId: String,
        body: Any,
        expectedRev: String?
    ): DocSuccess

    fun deleteDatabaseDocument(
        database: String,
        documentId: String
    ): DocSuccess

    fun <T : Any> getPreviousDocumentRevision(
        database: String,
        documentId: String,
        rev: String,
        kClass: KClass<T>
    ): Optional<T>

    fun createDatabase(databaseName: String)

    fun databaseExists(name: String): Boolean
}
