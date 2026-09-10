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
 * Runs on the change-detection thread. Rule matching is answered from [NotificationConfigCache] in
 * memory; the notifications it produces are recorded by the publisher rather than delivered inline,
 * so a slow mail or push endpoint cannot stall change detection.
 */
class NotificationDocumentChangeHandler(
    private val notificationConfigCache: NotificationConfigCache,
    private val applyNotificationRulesUseCase: ApplyNotificationRulesUseCase
) : AbstractDocumentChangeHandler() {
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
                logger.warn(
                    "ApplyNotificationRules failed for documentId={}: [{}] {}",
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
