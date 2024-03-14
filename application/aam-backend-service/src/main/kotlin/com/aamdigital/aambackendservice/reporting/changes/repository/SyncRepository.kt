package com.aamdigital.aambackendservice.reporting.changes.repository

import org.springframework.data.annotation.Id
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono

data class SyncEntry(
    @Id
    var id: Long = 0,
    var database: String,
    var latestRef: String,
)

interface SyncRepository : ReactiveCrudRepository<SyncEntry, String> {
    fun findByDatabase(database: String): Mono<SyncEntry>
}
