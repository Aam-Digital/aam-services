package com.aamdigital.aambackendservice.reporting.changes.jobs

import com.aamdigital.aambackendservice.reporting.changes.core.DatabaseChangeDetection
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled

@Configuration
class CouchDbChangeDetectionJob(
    private val databaseChangeDetection: DatabaseChangeDetection
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private var ERROR_COUNTER: Int = 0
        private var MAX_ERROR_COUNT: Int = 5
    }

    @Scheduled(fixedDelay = 15000)
    fun checkForCouchDbChanges() {
        if (ERROR_COUNTER >= MAX_ERROR_COUNT) {
            return
        }
        logger.trace("[CouchDbChangeDetectionJob] Starting job...")
        try {
            databaseChangeDetection.checkForChanges()
                .doOnError {
                    logger.error(
                        "[CouchDbChangeDetectionJob] An error occurred (count: $ERROR_COUNTER): {}",
                        it.localizedMessage
                    )
                    logger.debug("[CouchDbChangeDetectionJob] Debug information", it)
                    ERROR_COUNTER += 1
                }
                .subscribe {
                    logger.trace("[CouchDbChangeDetectionJob]: ...job completed.")
                }
        } catch (ex: Exception) {
            logger.error(
                "[CouchDbChangeDetectionJob] An error occurred {}",
                ex.localizedMessage
            )
            logger.debug("[CouchDbChangeDetectionJob] Debug information", ex)
        }
    }
}
