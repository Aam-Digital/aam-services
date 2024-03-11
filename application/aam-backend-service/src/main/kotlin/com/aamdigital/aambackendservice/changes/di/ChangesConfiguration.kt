package com.aamdigital.aambackendservice.changes.di

import com.aamdigital.aambackendservice.changes.core.ChangeEventPublisher
import com.aamdigital.aambackendservice.changes.core.CouchDatabaseChangeDetection
import com.aamdigital.aambackendservice.changes.core.CreateDocumentChangeUseCase
import com.aamdigital.aambackendservice.changes.core.DatabaseChangeDetection
import com.aamdigital.aambackendservice.changes.core.DefaultCreateDocumentChangeUseCase
import com.aamdigital.aambackendservice.changes.core.NoopDatabaseChangeDetection
import com.aamdigital.aambackendservice.changes.repository.SyncRepository
import com.aamdigital.aambackendservice.couchdb.core.CouchDbStorage
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
        couchDbStorage: CouchDbStorage,
        changeEventPublisher: ChangeEventPublisher,
        syncRepository: SyncRepository,
    ): DatabaseChangeDetection = CouchDatabaseChangeDetection(
        couchDbStorage,
        changeEventPublisher,
        syncRepository
    )

    @Bean
    fun defaultAnalyseDocumentChangeUseCase(
        couchDbStorage: CouchDbStorage,
        objectMapper: ObjectMapper,
        changeEventPublisher: ChangeEventPublisher
    ): CreateDocumentChangeUseCase =
        DefaultCreateDocumentChangeUseCase(
            couchDbStorage = couchDbStorage,
            objectMapper = objectMapper,
            documentChangeEventPublisher = changeEventPublisher,
        )
}
