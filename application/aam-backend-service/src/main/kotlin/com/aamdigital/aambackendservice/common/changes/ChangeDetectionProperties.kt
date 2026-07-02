package com.aamdigital.aambackendservice.common.changes

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Externalized configuration for [CouchDbChangesProcessor].
 *
 * [includedDatabases] is an allowlist: only databases whose name matches exactly are
 * polled for changes. This keeps auxiliary CouchDB databases (e.g. `audit`,
 * `notifications-*`, `app-attachments`) out of the `document.changes` fanout by default,
 * since only the core `app` database is relevant to current consumers.
 */
@ConfigurationProperties("database-change-detection")
class ChangeDetectionProperties(
    val includedDatabases: List<String> = listOf("app"),
)
