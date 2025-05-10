package com.aamdigital.aambackendservice.common.changes.jobs

import com.aamdigital.aambackendservice.common.changes.core.DatabaseChangeDetection
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

    @Scheduled(fixedDelay = 8000)
    fun checkForCouchDbChanges() {
        if (ERROR_COUNTER >= MAX_ERROR_COUNT) {
            logger.trace("[CouchDbChangeDetectionJob]: MAX_ERROR_COUNT reached. Not starting job again.")
            return
        }

        try {
            databaseChangeDetection.checkForChanges()
        } catch (ex: Exception) {
            logger.error(
                "[CouchDbChangeDetectionJob] An error occurred (count: $ERROR_COUNTER): {}",
                ex.localizedMessage
            )
            logger.debug("[CouchDbChangeDetectionJob] Debug information", ex)
            ERROR_COUNTER += 1
        }
    }
}
