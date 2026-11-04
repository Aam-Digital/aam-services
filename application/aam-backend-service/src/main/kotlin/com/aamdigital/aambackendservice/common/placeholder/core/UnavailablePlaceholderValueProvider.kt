package com.aamdigital.aambackendservice.common.placeholder.core

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Used when there is no source for placeholder values (Keycloak admin access is not configured):
 * no placeholder has a value, so configuration that uses one is ignored by its consumer.
 *
 * Warns once, on the first lookup that actually needs a placeholder, rather than at startup, so an
 * instance that uses no placeholders is not told about it.
 */
class UnavailablePlaceholderValueProvider : PlaceholderValueProvider {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val warned = AtomicBoolean(false)

    override fun getValues(
        userIdentifier: String,
        placeholders: Set<String>
    ): Map<String, Any> {
        if (placeholders.isNotEmpty() && warned.compareAndSet(false, true)) {
            logger.warn(
                "Placeholders {} are used, but cannot be resolved because Keycloak admin access " +
                    "(keycloak.server-url) is not configured. Configuration using them is ignored.",
                placeholders
            )
        }
        return emptyMap()
    }
}
