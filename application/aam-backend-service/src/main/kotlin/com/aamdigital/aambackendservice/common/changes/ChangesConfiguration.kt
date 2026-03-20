package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring configuration that wires up the change-detection beans.
 * The [CouchDbChangesProcessor] bean is only created when
 * `database-change-detection.enabled` is true (default).
 */
@Configuration
class ChangesConfiguration {
    @Bean
    @ConditionalOnProperty(
        prefix = "database-change-detection",
        name = ["enabled"],
        matchIfMissing = true
    )
    fun couchDatabaseChangeDetection(
        couchDbClient: CouchDbClient,
        changeEventPublisher: ChangeEventPublisher,
        syncRepository: SyncRepository,
        objectMapper: ObjectMapper,
    ): CouchDbChangesProcessor =
        CouchDbChangesProcessor(
            couchDbClient,
            changeEventPublisher,
            syncRepository,
            objectMapper,
        )
}
