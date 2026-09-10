package com.aamdigital.aambackendservice.common.changes

/**
 * Reacts to a document change detected by [CouchDbChangesProcessor].
 *
 * Every enabled feature module that cares about data changes contributes one handler bean, and
 * [CouchDbChangesProcessor] calls them all for each change. Because handler beans only exist when
 * their module is enabled, a disabled module simply contributes nothing.
 *
 * Handlers run **synchronously on the change-detection thread**, one change at a time, and the sync
 * cursor is only advanced once every handler has been given the change. Handlers must therefore not
 * do slow or unbounded work inline: hand it to a bounded executor or record it durably and let a
 * scheduled job pick it up (see `NotificationOutboxDrainer` and the report calculation executor).
 *
 * A handler is expected to deal with its own failures. An exception escaping [handle] is logged and
 * the remaining handlers still run, so one module cannot stall change detection for the others.
 */
interface DocumentChangeHandler {
    fun handle(event: DocumentChangeEvent)
}
