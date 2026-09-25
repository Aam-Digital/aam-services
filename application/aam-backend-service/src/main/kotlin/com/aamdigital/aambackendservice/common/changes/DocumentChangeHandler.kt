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
 * Implement this by extending [AbstractDocumentChangeHandler], which owns the logging and failure
 * disposition all handlers share and documents the threading contract they run under. This
 * interface stays the type the processor depends on, so a handler can be substituted in a test
 * without the base class.
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
