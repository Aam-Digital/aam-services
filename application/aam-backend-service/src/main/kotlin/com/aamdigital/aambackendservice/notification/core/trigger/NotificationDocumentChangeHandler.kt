package com.aamdigital.aambackendservice.notification.core.trigger

import com.aamdigital.aambackendservice.common.changes.AbstractDocumentChangeHandler
import com.aamdigital.aambackendservice.common.changes.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.notification.core.config.NotificationConfigCache

/**
 * Applies the users' notification rules to a document change.
 *
 * A change to a `NotificationConfig:*` document refreshes the rule cache instead of being matched
 * against the rules - it *is* the rules. A failed refresh leaves this instance working from the
 * rules it already has until it is restarted, which is why the shared ERROR-and-continue
 * disposition from [AbstractDocumentChangeHandler] is the right one here.
 *
 * Runs on the notification module's own change-detection thread, so nothing here can hold up
 * reporting. Rule matching is answered from [NotificationConfigCache] in memory; the notifications
 * it produces are recorded by the publisher rather than delivered inline, so a slow mail or push
 * endpoint cannot stall change detection. The permission check is the one external call made
 * inline, and its client is bounded by a timeout for that reason.
 */
class NotificationDocumentChangeHandler(
    private val notificationConfigCache: NotificationConfigCache,
    private val applyNotificationRulesUseCase: ApplyNotificationRulesUseCase
) : AbstractDocumentChangeHandler(consumerName = "notification") {
    override fun onChange(event: DocumentChangeEvent) {
        if (event.documentId.startsWith("NotificationConfig:")) {
            notificationConfigCache.refreshConfig(
                database = event.database,
                notificationConfigId = event.documentId,
                deleted = event.deleted
            )
            return
        }

        when (val result = applyNotificationRulesUseCase.run(ApplyNotificationRulesRequest(event))) {
            is UseCaseOutcome.Failure -> {
                // ERROR, not WARN, and deliberately not rethrown.
                //
                // Not rethrown because the cursor advances either way, so there is nothing to
                // redeliver: throwing would only reach the processor's backstop, which logs it
                // again. That makes the log line the *only* record that a user's notification was
                // owed and never produced - and WARN sits below the Sentry minimum event level, so
                // this used to drop notifications indefinitely against a green dashboard.
                //
                // Note DomainUseCase.run() turns every exception into a Failure, so this branch
                // covers transient infrastructure faults (CouchDB, Keycloak) as well as rule
                // outcomes. Making those recoverable means holding this module's cursor with a
                // bounded number of attempts, not a rethrow here.
                logger.error(
                    "ApplyNotificationRules failed for documentId={}, so no notification was " +
                        "created for any channel: [{}] {}",
                    event.documentId,
                    result.errorCode,
                    result.errorMessage,
                    result.cause
                )
            }

            is UseCaseOutcome.Success -> {
                logger.trace(
                    "ApplyNotificationRules completed for documentId={}: {} notifications triggered",
                    event.documentId,
                    result.data.notificationsSendCount
                )
            }
        }
    }
}
