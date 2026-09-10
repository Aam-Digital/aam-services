package com.aamdigital.aambackendservice.reporting.webhook.storage

import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration

/**
 * Caches only "which reports is any webhook subscribed to", for automatic change detection.
 *
 * [com.aamdigital.aambackendservice.reporting.report.queue.ReportDocumentChangeEventConsumer] needs
 * this answer for every single document change, and reading it through
 * [WebhookStorage.fetchAllWebhooks] costs a CouchDB `_all_docs` request plus one AES decrypt per
 * webhook every time. This cache reads [WebhookEntity] documents directly instead, so it never
 * decrypts and never holds a webhook secret in memory.
 *
 * Staleness is bounded from two sides:
 *
 * - [invalidate] is called by [DefaultWebhookStorage] after every write. That class is the only
 *   writer of the `notification-webhook` database, so an in-process subscribe, unsubscribe or
 *   create is reflected immediately. This is the direction that matters: a missed subscription
 *   would silently drop an automatic recalculation.
 * - [ttl] bounds staleness from writes this process cannot see (a direct CouchDB edit, a database
 *   restore, a second replica). Those can only produce a *stale subscription*, which costs one
 *   unnecessary recalculation, so a very short TTL is enough. It only has to outlast the
 *   processing of one change batch, which is all that is needed to collapse a batch of up to
 *   `CouchDbChangesProcessor.CHANGES_LIMIT` changes into a single lookup.
 *
 * A [ttl] of zero disables caching and reloads on every read.
 *
 * This cache is intentionally *not* consulted by `GET /v1/reporting/webhook` or by
 * `NotificationService`: those must never serve a stale webhook list.
 */
class WebhookSubscriptionCache(
    private val webhookRepository: WebhookRepository,
    private val ttl: Duration,
    private val clock: Clock = Clock.systemUTC()
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val cacheLock = Any()

    private var subscribedReportIds: Set<String>? = null
    private var loadedAtMillis: Long = 0

    /**
     * Ids of all reports that at least one webhook is subscribed to.
     *
     * Reloads from CouchDB when the cache is empty or older than [ttl], propagating the same
     * failures [WebhookRepository.fetchAllWebhooks] does. The reload runs inside the lock: the only
     * caller is the single-threaded reporting change-detection path, so there is no concurrency to
     * trade away and two callers can never issue the same reload twice.
     */
    fun subscribedReportIds(): Set<String> =
        synchronized(cacheLock) {
            val now = clock.millis()
            val cached = subscribedReportIds

            if (cached != null && now - loadedAtMillis < ttl.toMillis()) {
                return cached
            }

            val reloaded =
                webhookRepository
                    .fetchAllWebhooks()
                    .flatMap { entity -> entity.reportSubscriptions }
                    .toSet()

            subscribedReportIds = reloaded
            loadedAtMillis = now

            logger.trace("Loaded {} subscribed report ids into memory cache", reloaded.size)

            reloaded
        }

    /** Drops the cached snapshot so the next read goes to CouchDB. */
    fun invalidate() {
        synchronized(cacheLock) {
            subscribedReportIds = null
        }
    }
}
