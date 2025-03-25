package com.aamdigital.aambackendservice.couchdb.core

import com.aamdigital.aambackendservice.couchdb.dto.CouchDbChangesResponse
import com.aamdigital.aambackendservice.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.couchdb.dto.FindResponse
import com.aamdigital.aambackendservice.error.ExternalSystemException
import com.aamdigital.aambackendservice.error.NotFoundException
import org.springframework.http.HttpHeaders
import org.springframework.util.MultiValueMap
import java.io.InterruptedIOException
import java.util.*
import kotlin.reflect.KClass

interface CouchDbClient {
    fun allDatabases(): List<String>
    fun changes(database: String, queryParams: MultiValueMap<String, String>): CouchDbChangesResponse

    fun <T : Any> find(
        database: String,
        body: Map<String, Any>,
        queryParams: MultiValueMap<String, String> = getEmptyQueryParams(),
        kClass: KClass<T>
    ): FindResponse<T>

    fun headDatabaseDocument(
        database: String,
        documentId: String,
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
        kClass: KClass<T>,
    ): T

    fun putDatabaseDocument(
        database: String,
        documentId: String,
        body: Any
    ): DocSuccess

    fun <T : Any> getPreviousDocRev(
        database: String,
        documentId: String,
        rev: String,
        kClass: KClass<T>,
    ): Optional<T>

    fun createDatabase(databaseName: String)

    fun databaseExists(name: String): Boolean
}
