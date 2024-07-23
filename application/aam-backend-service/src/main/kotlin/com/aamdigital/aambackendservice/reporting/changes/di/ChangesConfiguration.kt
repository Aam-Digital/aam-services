package com.aamdigital.aambackendservice.reporting.changes.di

import com.aamdigital.aambackendservice.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.reporting.changes.core.ChangeEventPublisher
import com.aamdigital.aambackendservice.reporting.changes.core.CouchDbDatabaseChangeDetection
import com.aamdigital.aambackendservice.reporting.changes.core.CreateDocumentChangeUseCase
import com.aamdigital.aambackendservice.reporting.changes.core.DatabaseChangeDetection
import com.aamdigital.aambackendservice.reporting.changes.core.DefaultCreateDocumentChangeUseCase
import com.aamdigital.aambackendservice.reporting.changes.core.NoopDatabaseChangeDetection
import com.aamdigital.aambackendservice.reporting.changes.repository.SyncRepository
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
    ): DatabaseChangeDetection = CouchDbDatabaseChangeDetection(
        couchDbClient,
        changeEventPublisher,
        syncRepository
    )

    @Bean
    fun defaultAnalyseDocumentChangeUseCase(
        couchDbClient: CouchDbClient,
        objectMapper: ObjectMapper,
        changeEventPublisher: ChangeEventPublisher
    ): CreateDocumentChangeUseCase =
        DefaultCreateDocumentChangeUseCase(
            couchDbClient = couchDbClient,
            objectMapper = objectMapper,
            documentChangeEventPublisher = changeEventPublisher,
        )
}
