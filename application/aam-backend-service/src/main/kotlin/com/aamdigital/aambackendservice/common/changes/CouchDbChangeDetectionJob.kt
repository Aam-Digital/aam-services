package com.aamdigital.aambackendservice.common.changes

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled

@Configuration
class CouchDbChangeDetectionJob(
    private val databaseChangeDetection: CouchDbDatabaseChangeDetection
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private var errorCounter: Int = 0
    private val maxErrorCount: Int = 5

    @Scheduled(fixedDelayString = "\${database-change-detection.fixed-delay:8000}")
    fun checkForCouchDbChanges() {
        if (errorCounter >= maxErrorCount) {
            logger.trace("[CouchDbChangeDetectionJob]: maxErrorCount reached. Not starting job again.")
            return
        }

        try {
            databaseChangeDetection.checkForChanges()
            errorCounter = 0
        } catch (ex: Exception) {
            logger.error(
                "[CouchDbChangeDetectionJob] An error occurred (count: $errorCounter): {}",
                ex.localizedMessage
            )
            logger.debug("[CouchDbChangeDetectionJob] Debug information", ex)
            errorCounter += 1
        }
    }
}
