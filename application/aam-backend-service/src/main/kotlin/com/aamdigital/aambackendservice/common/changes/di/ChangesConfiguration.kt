package com.aamdigital.aambackendservice.common.changes.di

import com.aamdigital.aambackendservice.common.changes.core.ChangeEventPublisher
import com.aamdigital.aambackendservice.common.changes.core.CouchDbDatabaseChangeDetection
import com.aamdigital.aambackendservice.common.changes.core.DatabaseChangeDetection
import com.aamdigital.aambackendservice.common.changes.core.NoopDatabaseChangeDetection
import com.aamdigital.aambackendservice.common.changes.repository.SyncRepository
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
