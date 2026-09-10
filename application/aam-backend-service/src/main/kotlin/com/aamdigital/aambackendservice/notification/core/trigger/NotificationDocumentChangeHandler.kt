package com.aamdigital.aambackendservice.notification.core.trigger

import com.aamdigital.aambackendservice.common.changes.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.changes.DocumentChangeHandler
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.notification.core.config.NotificationConfigCache
import org.slf4j.LoggerFactory

/**
 * Applies the users' notification rules to a document change.
 *
 * A change to a `NotificationConfig:*` document refreshes the rule cache instead of being matched
 * against the rules - it *is* the rules.
 *
 * Runs on the change-detection thread. Rule matching is answered from [NotificationConfigCache] in
 * memory; the notifications it produces are recorded by the publisher rather than delivered inline,
 * so a slow mail or push endpoint cannot stall change detection.
 */
class NotificationDocumentChangeHandler(
    private val notificationConfigCache: NotificationConfigCache,
    private val applyNotificationRulesUseCase: ApplyNotificationRulesUseCase
) : DocumentChangeHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun handle(event: DocumentChangeEvent) {
        if (event.documentId.startsWith("NotificationConfig:")) {
            refreshNotificationConfig(event)
            return
        }

        when (val result = applyNotificationRulesUseCase.run(ApplyNotificationRulesRequest(event))) {
            is UseCaseOutcome.Failure ->
                logger.warn(
                    "ApplyNotificationRules failed for documentId={}: [{}] {}",
                    event.documentId,
                    result.errorCode,
                    result.errorMessage,
                    result.cause
                )

            is UseCaseOutcome.Success ->
                logger.trace(
                    "ApplyNotificationRules completed for documentId={}: {} notifications triggered",
                    event.documentId,
                    result.data.notificationsSendCount
                )
        }
    }

    private fun refreshNotificationConfig(event: DocumentChangeEvent) {
        logger.trace(
            "Refreshing notification config cache for db={}, documentId={}, rev={}, deleted={}",
            event.database,
            event.documentId,
            event.rev,
            event.deleted
        )

        try {
            notificationConfigCache.refreshConfig(
                database = event.database,
                notificationConfigId = event.documentId,
                deleted = event.deleted
            )
        } catch (ex: Exception) {
            // The cache is reloaded on startup, so a failed refresh means this instance keeps
            // working from the previous rules until it is restarted - the same outcome as before,
            // when the rejected message was dropped by a queue with no dead letter queue.
            logger.error(
                "Failed to refresh notification config cache for db={}, documentId={}, rev={}, deleted={}",
                event.database,
                event.documentId,
                event.rev,
                event.deleted,
                ex
            )
        }
    }
}
