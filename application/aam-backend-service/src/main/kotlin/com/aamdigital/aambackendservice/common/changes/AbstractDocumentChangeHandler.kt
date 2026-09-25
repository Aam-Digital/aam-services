package com.aamdigital.aambackendservice.common.changes

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Base class for [DocumentChangeHandler]s, owning the failure disposition that every handler shares.
 *
 * Modelled on [com.aamdigital.aambackendservice.common.domain.DomainUseCase]: [handle] is final and
 * wraps [onChange] in the shared error handling, so a subclass writes only its module's reaction to
 * a change and cannot accidentally end up with a different disposition than its siblings. Override
 * [errorHandler] where a module genuinely needs one.
 *
 * The default disposition is to log at ERROR and continue, so one document a module cannot handle
 * does not stall that module's feed. The consequence is worth being explicit about: the handler's
 * cursor advances regardless, so a change it failed on is **not** redelivered to it. Each handler
 * has its own cursor, so redelivery would be possible - but it means holding the cursor with a
 * bounded number of attempts, since holding it indefinitely would let one poison document stop
 * the module for good. That is a deliberate follow-up, not something an override here can express.
 *
 * Handlers run **synchronously on their own polling thread**, one change at a time, and their
 * cursor is advanced once the change has been handled. A slow handler therefore delays only its own
 * module, but it does delay it: slow or unbounded work must not run inline. Hand it to a bounded
 * executor, or record it durably and let a scheduled job pick it up (see `NotificationOutboxDrainer`
 * and the report calculation executor), and give any external call inline a timeout.
 *
 * @param consumerName see [DocumentChangeHandler.consumerName] - persisted, so never rename it
 */
abstract class AbstractDocumentChangeHandler(
    final override val consumerName: String
) : DocumentChangeHandler {
    protected val logger: Logger = LoggerFactory.getLogger(javaClass)

    final override fun handle(event: DocumentChangeEvent) {
        logger.trace(
            "handling change db={}, documentId={}, rev={}, deleted={}",
            event.database,
            event.documentId,
            event.rev,
            event.deleted
        )

        try {
            onChange(event)
        } catch (ex: Exception) {
            errorHandler(event, ex)
        }
    }

    /**
     * React to the change. Anything thrown here is passed to [errorHandler] rather than reaching
     * [CouchDbChangesProcessor], so a subclass does not need its own catch-all.
     */
    protected abstract fun onChange(event: DocumentChangeEvent)

    /** optional extend default error handling */
    protected open fun errorHandler(
        event: DocumentChangeEvent,
        ex: Exception
    ) {
        logger.error(
            "Failed to handle change db={}, documentId={}, rev={}: {}",
            event.database,
            event.documentId,
            event.rev,
            ex.message,
            ex
        )
    }
}
