package com.aamdigital.aambackendservice.couchdb.core

import com.aamdigital.aambackendservice.couchdb.dto.CouchDbChangesResponse
import com.aamdigital.aambackendservice.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.couchdb.dto.FindResponse
import org.springframework.http.HttpHeaders
import org.springframework.util.MultiValueMap
import reactor.core.publisher.Mono
import kotlin.reflect.KClass

interface CouchDbStorage {
    fun allDatabases(): Mono<List<String>>
    fun changes(database: String, queryParams: MultiValueMap<String, String>): Mono<CouchDbChangesResponse>

    fun <T : Any> find(
        database: String,
        body: Map<String, Any>,
        queryParams: MultiValueMap<String, String>,
        kClass: KClass<T>
    ): Mono<FindResponse<T>>

    fun headDatabaseDocument(
        database: String,
        documentId: String,
    ): Mono<HttpHeaders>

    fun <T : Any> getDatabaseDocument(
        database: String,
        documentId: String,
        queryParams: MultiValueMap<String, String>,
        kClass: KClass<T>,
    ): Mono<T>

    fun putDatabaseDocument(
        database: String,
        documentId: String,
        body: Any
    ): Mono<DocSuccess>

    fun <T : Any> getPreviousDocRev(
        database: String,
        documentId: String,
        rev: String,
        kClass: KClass<T>,
    ): Mono<T>
}
