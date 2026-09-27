package com.aamdigital.aambackendservice.common.scheduling

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/**
 * Provides an explicit pooled [TaskScheduler] for all `@Scheduled` jobs.
 *
 * With `spring.threads.virtual.enabled=true`, Spring Boot would otherwise auto-configure a
 * `SimpleAsyncTaskScheduler` that runs every `fixedDelay` task on a single shared scheduler
 * thread. All scheduled jobs here use `fixedDelay`, so they would serialize on that one thread:
 * a slow flush in [com.aamdigital.aambackendservice.reporting.reportcalculation.job.ReportCalculationDebounceJob]
 * (which does CouchDB I/O per due report) could then delay the frequent
 * [com.aamdigital.aambackendservice.common.changes.CouchDbChangesPollingJob] and stall change detection.
 *
 * Defining this bean makes it the [TaskScheduler] used for `@Scheduled` (the auto-configuration
 * backs off on the existing bean), giving the jobs their own pool so they no longer block each
 * other. A task stuck on a slow dependency holds its thread, and must not leave another task waiting
 * for one, so the pool has a thread for every scheduled task with all feature modules enabled:
 *
 * - [com.aamdigital.aambackendservice.common.changes.CouchDbChangesPollingJob], once per change
 *   consumer (reporting, notification)
 * - [com.aamdigital.aambackendservice.notification.job.NotificationOutboxDrainJob]
 * - [com.aamdigital.aambackendservice.reporting.reportcalculation.job.ReportCalculationSweepJob]
 * - [com.aamdigital.aambackendservice.reporting.reportcalculation.job.ReportCalculationDebounceJob]
 * - [com.aamdigital.aambackendservice.skill.job.SyncSkillsJob]
 *
 * That is [SCHEDULED_TASKS]; the pool adds [SPARE_THREADS] so one new job does not silently
 * start sharing threads. Update the list and the count when adding a job or a change consumer.
 * This only affects scheduling; web request handling and `@Async` keep using virtual threads.
 */
@Configuration
class SchedulingConfiguration {
    companion object {
        private const val SCHEDULED_TASKS = 6
        private const val SPARE_THREADS = 2
    }

    @Bean
    fun taskScheduler(): TaskScheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = SCHEDULED_TASKS + SPARE_THREADS
            setThreadNamePrefix("scheduled-")
        }
}
