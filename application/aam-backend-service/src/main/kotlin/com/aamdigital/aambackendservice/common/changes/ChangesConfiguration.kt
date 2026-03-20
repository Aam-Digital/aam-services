package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ChangesConfiguration {
    @Bean
    @ConditionalOnProperty(
        prefix = "database-change-detection",
        name = ["enabled"],
        havingValue = "false"
    )
    fun noopDatabaseChangeDetection(): DatabaseChangeDetection = NoopDatabaseChangeDetection()

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
    ): DatabaseChangeDetection =
        CouchDbDatabaseChangeDetection(
            couchDbClient,
            changeEventPublisher,
            syncRepository,
            objectMapper,
        )
}
