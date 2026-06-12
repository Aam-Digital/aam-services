package com.aamdigital.aambackendservice.common.domain

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Basic application configuration to use across all modules.
 */
@ConfigurationProperties("application")
class ApplicationConfig(
    /** the base url of the app (e.g. dev.aam-digital.net) */
    val baseUrl: String
) {
    /**
     * Returns [baseUrl] with an explicit protocol, defaulting to https:// when missing.
     */
    val normalizedBaseUrl: String
        get() = normalizeUrlWithHttpsDefault(baseUrl)
}
