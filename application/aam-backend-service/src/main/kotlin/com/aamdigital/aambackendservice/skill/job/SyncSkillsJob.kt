package com.aamdigital.aambackendservice.skill.job

import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesRequest
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesUseCase
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled

@Configuration
class SyncSkillsJob(
    private val skillLabFetchUserProfileUpdatesUseCase: FetchUserProfileUpdatesUseCase
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private var ERROR_COUNTER: Int = 0
        private var MAX_ERROR_COUNT: Int = 5
    }

    @Scheduled(fixedDelay = (60000 * 10))
    fun checkForCouchDbChanges() {
        if (ERROR_COUNTER >= MAX_ERROR_COUNT) {
            logger.trace("${this.javaClass.name}: MAX_ERROR_COUNT reached. Not starting job again.")
            return
        }

        try {
            skillLabFetchUserProfileUpdatesUseCase.run(
                request = FetchUserProfileUpdatesRequest(
                    projectId = "343"
                )
            )
        } catch (ex: Exception) {
            logger.error(
                "[${this.javaClass.name}] An error occurred (count: $ERROR_COUNTER): {}",
                ex.localizedMessage
            )
            logger.debug("[${this.javaClass.name}] Debug information", ex)
            ERROR_COUNTER += 1
        }
    }
}
