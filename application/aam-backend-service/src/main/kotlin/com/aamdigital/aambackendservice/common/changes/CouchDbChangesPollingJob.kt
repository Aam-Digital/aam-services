package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.scheduling.ScheduledJobBackoff
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled

/**
 * Scheduled job that invokes [CouchDbChangesProcessor.checkForChanges] on a fixed interval.
 * Uses exponential backoff on consecutive failures (capped at one retry per day).
 * Resets the error counter on each successful run.
 *
 * Active whenever change-detection itself is active (see [ChangesConfiguration]).
 */
@Configuration
@ConditionalOnBean(CouchDbChangesProcessor::class)
class CouchDbChangesPollingJob(
    private val changesProcessor: CouchDbChangesProcessor,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    internal val backoff = ScheduledJobBackoff(logger, "CouchDbChangesPollingJob")

    @Scheduled(fixedDelayString = "\${database-change-detection.fixed-delay:8000}")
    fun checkForCouchDbChanges() {
        if (backoff.shouldSkip()) return

        backoff.execute {
            changesProcessor.checkForChanges()
        }
    }
}
