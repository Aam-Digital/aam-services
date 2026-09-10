package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
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
 * one feature module that consumes document changes is enabled.
 *
 * If a new feature module starts consuming document changes, add a nested
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

    /**
     * @param documentChangeHandlers every enabled module's handler. An [ObjectProvider] rather than
     *     a `List` so that a gate above which no module happens to contribute a handler yields an
     *     empty list instead of failing to start.
     */
    @Bean
    fun couchDatabaseChangeDetection(
        couchDbClient: CouchDbClient,
        documentChangeHandlers: ObjectProvider<DocumentChangeHandler>,
        syncRepository: SyncRepository,
        objectMapper: ObjectMapper,
        changeDetectionProperties: ChangeDetectionProperties,
    ): CouchDbChangesProcessor =
        CouchDbChangesProcessor(
            couchDbClient,
            documentChangeHandlers.orderedStream().toList(),
            syncRepository,
            objectMapper,
            changeDetectionProperties,
        )
}
