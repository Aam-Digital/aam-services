package com.aamdigital.aambackendservice.notification.core.trigger

/** Human-readable label of an entity type, as configured for the frontend. */
fun interface EntityTypeLabelProvider {
    /** @return the configured label, or null if none is configured or it cannot be read */
    fun labelFor(entityType: String): String?

    /** Drops any cached labels, e.g. because the entity config has changed. */
    fun invalidate() {}
}
