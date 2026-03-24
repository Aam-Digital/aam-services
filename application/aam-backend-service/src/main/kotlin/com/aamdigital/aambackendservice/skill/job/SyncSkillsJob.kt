package com.aamdigital.aambackendservice.skill.job

import com.aamdigital.aambackendservice.common.scheduling.ScheduledJobBackoff
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesRequest
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesUseCase
import com.aamdigital.aambackendservice.skill.di.SkillLabApiClientConfiguration
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled

/**
 * Configures cron job for scheduled sync all SkillLab changes.
 * Uses exponential backoff on consecutive failures (capped at one retry per day).
 * Resets the error counter on each successful run.
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
    internal val backoff = ScheduledJobBackoff(logger, "SyncSkillsJob")

    @Scheduled(fixedDelay = (60000 * 10)) // every 10 minutes
    fun checkForSkillLabChanges() {
        if (backoff.shouldSkip()) return

        backoff.execute {
            skillLabFetchUserProfileUpdatesUseCase.run(
                request =
                    FetchUserProfileUpdatesRequest(
                        projectId = skillLabApiClientConfiguration.projectId
                    )
            )
        }
    }
}
