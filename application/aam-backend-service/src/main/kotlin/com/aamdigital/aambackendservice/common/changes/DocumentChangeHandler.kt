package com.aamdigital.aambackendservice.common.changes

/**
 * Reacts to a document change detected by [CouchDbChangesProcessor].
 *
 * Every enabled feature module that cares about data changes contributes one handler bean, and
 * [CouchDbChangesProcessor] calls them all for each change. Because handler beans only exist when
 * their module is enabled, a disabled module simply contributes nothing.
 *
 * Implement this by extending [AbstractDocumentChangeHandler], which owns the logging and failure
 * disposition all handlers share and documents the threading contract they run under. This
 * interface stays the type the processor depends on, so a handler can be substituted in a test
 * without the base class.
 */
interface DocumentChangeHandler {
    fun handle(event: DocumentChangeEvent)
}
