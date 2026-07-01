package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase

/**
 * Spring configuration that wires up the change-detection beans.
 *
 * The [CouchDbChangesProcessor] bean is created automatically whenever at least
 * one feature module that consumes `document.changes` events is enabled.
 *
 * If a new feature module starts consuming `document.changes`, add a nested
 * condition to [AnyChangeConsumerEnabled] so change-detection turns on with it.
 */
@Configuration
@Conditional(ChangesConfiguration.AnyChangeConsumerEnabled::class)
class ChangesConfiguration {

    class AnyChangeConsumerEnabled : AnyNestedCondition(ConfigurationPhase.PARSE_CONFIGURATION) {
        @ConditionalOnProperty("features.reporting.enabled", havingValue = "true")
        class Reporting

        @ConditionalOnProperty("features.notification-api.enabled", havingValue = "true")
        class NotificationApi
    }

    @Bean
    fun couchDatabaseChangeDetection(
        couchDbClient: CouchDbClient,
        changeEventPublisher: ChangeEventPublisher,
        syncRepository: SyncRepository,
        objectMapper: ObjectMapper,
        changeDetectionProperties: ChangeDetectionProperties,
    ): CouchDbChangesProcessor =
        CouchDbChangesProcessor(
            couchDbClient,
            changeEventPublisher,
            syncRepository,
            objectMapper,
            changeDetectionProperties,
        )
}
