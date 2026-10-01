package com.aamdigital.aambackendservice.common.changes

/**
 * Reacts to a document change detected by [CouchDbChangesProcessor].
 *
 * Every enabled feature module that cares about data changes contributes one handler bean. Each
 * handler reads the change feed on its own: it has its own cursor and its own polling task (see
 * [CouchDbChangesPollingJob]), so a module that is slow or stuck holds back only itself. Because
 * handler beans only exist when their module is enabled, a disabled module simply contributes
 * nothing.
 *
 * Handlers run **synchronously on their own polling thread**, one change at a time, and their
 * cursor is advanced once the change has been handled. A slow handler therefore delays only its own
 * module, but it does delay it: slow or unbounded work must not run inline. Hand it to a bounded
 * executor (see the report calculation executor), or record it durably in an
 * [com.aamdigital.aambackendservice.common.outbox.Outbox] for a scheduled job to deliver, and give
 * any external call inline a timeout.
 *
 * An exception thrown by [handle] is logged at ERROR by [CouchDbChangesProcessor], and the cursor
 * advances regardless, so one document a module cannot handle does not stall that module's feed.
 * The consequence is that a change a handler failed on is **not** redelivered to it: a handler
 * needs no catch-all of its own, but it owns its recovery.
 */
interface DocumentChangeHandler {
    /**
     * Names this handler's own position in the change feed, and must be unique among handlers.
     *
     * It is persisted as part of the cursor's document id (`SyncEntry:<database>:<consumerName>`),
     * so renaming it makes the handler start again from "now" and skip whatever changed in between.
     */
    val consumerName: String

    fun handle(event: DocumentChangeEvent)
}
