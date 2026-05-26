package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring configuration that wires up the change-detection beans.
 *
 * The [CouchDbChangesProcessor] bean is created automatically whenever at least
 * one feature module that consumes `document.changes` events is enabled.
 * Currently those consumers are the reporting and notification modules.
 *
 * If a new feature module starts consuming `document.changes`, add its feature
 * flag to the expression below so change-detection turns on with it.
 */
@Configuration
@ConditionalOnExpression(
    "\${features.reporting.enabled:false} or \${features.notification-api.enabled:false}"
)
class ChangesConfiguration {
    @Bean
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
