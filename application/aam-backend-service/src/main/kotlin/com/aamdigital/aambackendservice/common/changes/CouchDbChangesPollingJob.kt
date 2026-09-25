package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.scheduling.ScheduledJobBackoff
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import java.time.Duration

/**
 * Polls the CouchDB changes feed separately for every [DocumentChangeHandler]: one fixed-delay task
 * per handler, each on its own thread of the scheduler pool and with its own [ChangeConsumerPoller]
 * backoff.
 *
 * That separation is the point. Handlers run synchronously on the thread that polls for them, so
 * with a single shared poll a handler blocked on a slow dependency - the notification module's
 * permission check, say - would stop change detection for every module. Polled separately, each
 * with its own cursor, a stuck module holds back only itself, which is what the separate queues per
 * module used to guarantee.
 *
 * Registered as a [SchedulingConfigurer] rather than with `@Scheduled`, because the number of tasks
 * depends on which modules are enabled. The scheduler pool is sized for one task per handler (see
 * `SchedulingConfiguration`).
 */
class CouchDbChangesPollingJob(
    private val changesProcessor: CouchDbChangesProcessor,
    documentChangeHandlers: List<DocumentChangeHandler>,
    private val sharedSyncEntryMigration: SharedSyncEntryMigration,
    private val fixedDelay: Duration
) : SchedulingConfigurer {
    internal val pollers: List<ChangeConsumerPoller>

    init {
        val duplicateNames =
            documentChangeHandlers
                .groupBy { handler -> handler.consumerName }
                .filterValues { handlers -> handlers.size > 1 }
                .keys
        require(duplicateNames.isEmpty()) {
            "Every DocumentChangeHandler needs its own consumerName, since it names the handler's cursor; " +
                "used more than once: $duplicateNames"
        }

        pollers =
            documentChangeHandlers.map { handler ->
                ChangeConsumerPoller(handler.consumerName) {
                    sharedSyncEntryMigration.migrateIfPending()
                    changesProcessor.checkForChanges(handler)
                }
            }
    }

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        pollers.forEach { poller ->
            taskRegistrar.addFixedDelayTask(poller::run, fixedDelay)
        }
    }
}

/**
 * One consumer's poll, with exponential backoff on consecutive failures (capped at one retry per
 * day) that resets on the next successful run. The backoff is per consumer, so one consumer
 * failing does not slow down the others.
 */
class ChangeConsumerPoller(
    val consumerName: String,
    private val poll: () -> Unit
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    internal val backoff = ScheduledJobBackoff(logger, "CouchDbChangesPollingJob:$consumerName")

    fun run() {
        if (backoff.shouldSkip()) return

        backoff.execute(poll)
    }
}
