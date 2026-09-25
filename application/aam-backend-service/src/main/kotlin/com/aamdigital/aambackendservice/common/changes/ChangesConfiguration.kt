package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase
import java.time.Duration

/**
 * Spring configuration that wires up the change-detection beans.
 *
 * The [CouchDbChangesProcessor] and [CouchDbChangesPollingJob] beans are created automatically
 * whenever at least one feature module that consumes document changes is enabled.
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

    @Bean
    fun syncRepository(couchDbClient: CouchDbClient): SyncRepository = CouchDbSyncRepository(couchDbClient)

    @Bean
    fun couchDatabaseChangeDetection(
        couchDbClient: CouchDbClient,
        syncRepository: SyncRepository,
        objectMapper: ObjectMapper,
        changeDetectionProperties: ChangeDetectionProperties
    ): CouchDbChangesProcessor =
        CouchDbChangesProcessor(
            couchDbClient,
            syncRepository,
            objectMapper,
            changeDetectionProperties
        )

    @Bean
    fun sharedSyncEntryMigration(
        syncRepository: SyncRepository,
        documentChangeHandlers: ObjectProvider<DocumentChangeHandler>,
        changeDetectionProperties: ChangeDetectionProperties
    ): SharedSyncEntryMigration =
        SharedSyncEntryMigration(
            syncRepository = syncRepository,
            databases = changeDetectionProperties.includedDatabases,
            consumerNames = documentChangeHandlers.orderedStream().map { it.consumerName }.toList()
        )

    /**
     * @param documentChangeHandlers every enabled module's handler. An [ObjectProvider] rather than
     *     a `List` so that a gate above which no module happens to contribute a handler yields an
     *     empty list instead of failing to start.
     */
    @Bean
    fun couchDbChangesPollingJob(
        changesProcessor: CouchDbChangesProcessor,
        documentChangeHandlers: ObjectProvider<DocumentChangeHandler>,
        sharedSyncEntryMigration: SharedSyncEntryMigration,
        @Value("\${database-change-detection.fixed-delay:8000}") fixedDelayMillis: Long
    ): CouchDbChangesPollingJob =
        CouchDbChangesPollingJob(
            changesProcessor = changesProcessor,
            documentChangeHandlers = documentChangeHandlers.orderedStream().toList(),
            sharedSyncEntryMigration = sharedSyncEntryMigration,
            fixedDelay = Duration.ofMillis(fixedDelayMillis)
        )
}
