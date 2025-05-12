package com.aamdigital.aambackendservice.common.domain

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Basic application configuration to use across all modules.
 */
@ConfigurationProperties("application")
class ApplicationConfig(
    /** the base url of the app (e.g. dev.aam-digital.net) */
    val baseUrl: String,
)
