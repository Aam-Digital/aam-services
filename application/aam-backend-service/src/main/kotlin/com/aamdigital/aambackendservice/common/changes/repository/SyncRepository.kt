package com.aamdigital.aambackendservice.common.changes.repository

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Store latest sync sequence for each database for change detection
 */
@Entity(name = "couchdb_sync_entry")
data class SyncEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    var id: Long = 0,
    var database: String,
    @Column(columnDefinition = "TEXT")
    var latestRef: String,
)

@Repository
interface SyncRepository : CrudRepository<SyncEntry, String> {
    fun findByDatabase(database: String): Optional<SyncEntry>
}
