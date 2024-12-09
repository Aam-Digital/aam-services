package com.aamdigital.aambackendservice.skill.job

import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesRequest
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesUseCase
import com.aamdigital.aambackendservice.skill.di.SkillLabApiClientConfiguration
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled

/**
 * Configures cron job for scheduled sync all SkillLab changes
 */
@Configuration
@ConditionalOnProperty(
    prefix = "features.skill-api",
    name = ["mode"],
    havingValue = "skilllab",
    matchIfMissing = false
)
class SyncSkillsJob(
    private val skillLabFetchUserProfileUpdatesUseCase: FetchUserProfileUpdatesUseCase,
    private val skillLabApiClientConfiguration: SkillLabApiClientConfiguration,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private var ERROR_COUNTER: Int = 0
        private var MAX_ERROR_COUNT: Int = 5
    }

    @Scheduled(fixedDelay = (60000 * 10)) // every 10 minutes
    fun checkForSkillLabChanges() {
        if (ERROR_COUNTER >= MAX_ERROR_COUNT) {
            logger.trace("${this.javaClass.name}: MAX_ERROR_COUNT reached. Not starting job again.")
            return
        }

        try {
            skillLabFetchUserProfileUpdatesUseCase.run(
                request = FetchUserProfileUpdatesRequest(
                    projectId = skillLabApiClientConfiguration.projectId
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
