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
 * other. The pool is sized to the number of scheduled jobs, counting the ones that only exist when
 * their feature module is enabled
 * (`[com.aamdigital.aambackendservice.notification.queue.NotificationDlqReprocessor]`). This only
 * affects scheduling; web request handling and `@Async` keep using virtual threads.
 */
@Configuration
class SchedulingConfiguration {
    @Bean
    fun taskScheduler(): TaskScheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = 4
            setThreadNamePrefix("scheduled-")
        }
}
