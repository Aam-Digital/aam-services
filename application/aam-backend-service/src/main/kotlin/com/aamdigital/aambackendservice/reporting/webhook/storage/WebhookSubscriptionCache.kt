package com.aamdigital.aambackendservice.reporting.webhook.storage

import com.aamdigital.aambackendservice.common.cache.LazySnapshot
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration

/**
 * Caches only "which reports is any webhook subscribed to", for automatic change detection.
 *
 * [com.aamdigital.aambackendservice.reporting.report.core.ReportDocumentChangeHandler] needs
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
    ttl: Duration,
    clock: Clock = Clock.systemUTC()
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val snapshot = LazySnapshot(ttl, clock) { loadSubscribedReportIds() }

    /**
     * Ids of all reports that at least one webhook is subscribed to.
     *
     * Reloads from CouchDB when nothing is cached or the copy is older than the ttl, propagating
     * the same failures [WebhookRepository.fetchAllWebhooks] does.
     */
    fun subscribedReportIds(): Set<String> = snapshot.get()

    /** Drops the cached snapshot so the next read goes to CouchDB. */
    fun invalidate() = snapshot.invalidate()

    private fun loadSubscribedReportIds(): Set<String> =
        webhookRepository
            .fetchAllWebhooks()
            .flatMap { entity -> entity.reportSubscriptions }
            .toSet()
            .also { loaded -> logger.trace("Loaded {} subscribed report ids into memory cache", loaded.size) }
}
