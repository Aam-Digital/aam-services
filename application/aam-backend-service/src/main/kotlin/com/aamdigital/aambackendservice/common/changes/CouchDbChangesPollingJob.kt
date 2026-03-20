package com.aamdigital.aambackendservice.common.changes

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled

/**
 * Scheduled job that invokes [CouchDbChangesProcessor.checkForChanges] on a fixed interval.
 * Stops polling after [maxErrorCount] consecutive failures to avoid flooding logs.
 * Resets the error counter on each successful run.
 */
@Configuration
class CouchDbChangesPollingJob(
    private val databaseChangeDetection: CouchDbChangesProcessor
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private var errorCounter: Int = 0
    private val maxErrorCount: Int = 5

    @Scheduled(fixedDelayString = "\${database-change-detection.fixed-delay:8000}")
    fun checkForCouchDbChanges() {
        if (errorCounter >= maxErrorCount) {
            logger.trace("[CouchDbChangesPollingJob]: maxErrorCount reached. Not starting job again.")
            return
        }

        try {
            databaseChangeDetection.checkForChanges()
            errorCounter = 0
        } catch (ex: Exception) {
            logger.error(
                "[CouchDbChangesPollingJob] An error occurred (count: $errorCounter): {}",
                ex.localizedMessage
            )
            logger.debug("[CouchDbChangesPollingJob] Debug information", ex)
            errorCounter += 1
        }
    }
}
