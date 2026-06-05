package com.aamdigital.aambackendservice.export

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/**
 * Activates the annotated bean only when the export-api feature flag is enabled.
 * Single source of truth for the `features.export-api.enabled` property.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(
    prefix = "features.export-api",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
annotation class ConditionalOnExportApiEnabled
