package com.aamdigital.aambackendservice.reporting

import com.aamdigital.aambackendservice.common.security.AamAuthorities
import org.springframework.security.access.prepost.PreAuthorize

/**
 * Keycloak client scopes granting an API client access to the reporting endpoints.
 */
object ReportingScopes {
    const val READ = "reporting_read"
    const val WRITE = "reporting_write"
}

/**
 * Read access to the reporting API:
 * an API client with the [ReportingScopes.READ] client scope, or a user of the Aam Digital frontend app.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize(
    "hasAnyAuthority('${AamAuthorities.SCOPE_PREFIX}${ReportingScopes.READ}', '${AamAuthorities.FRONTEND_CLIENT}')"
)
annotation class RequiresReportingReadAccess

/**
 * Write access to the reporting API (triggering calculations, managing webhooks):
 * an API client with the [ReportingScopes.WRITE] client scope, or a user of the Aam Digital frontend app.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize(
    "hasAnyAuthority('${AamAuthorities.SCOPE_PREFIX}${ReportingScopes.WRITE}', '${AamAuthorities.FRONTEND_CLIENT}')"
)
annotation class RequiresReportingWriteAccess
