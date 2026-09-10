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
 * The default disposition is to log at ERROR and continue. Change detection feeds several
 * independent modules, so one module failing on one document must neither stop the others nor stall
 * the feed, and each module owns its own recovery. The consequence is worth being explicit about:
 * the sync cursor advances regardless, so a change a handler failed on is **not** redelivered to it.
 * Making that redeliverable needs a per-handler cursor rather than a per-handler exception, which is
 * a deliberate follow-up and not something an override here can express today.
 *
 * Handlers run **synchronously on the change-detection thread**, one change at a time, and the
 * cursor is advanced only once every handler has been given the change. Slow or unbounded work
 * therefore must not run inline: hand it to a bounded executor, or record it durably and let a
 * scheduled job pick it up (see `NotificationOutboxDrainer` and the report calculation executor).
 */
abstract class AbstractDocumentChangeHandler : DocumentChangeHandler {
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
