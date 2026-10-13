package com.aamdigital.aambackendservice.apiclient

import com.aamdigital.aambackendservice.common.security.AamAuthorities
import com.aamdigital.aambackendservice.common.security.AamRealmRoles
import org.springframework.security.access.prepost.PreAuthorize

/**
 * Access to manage the API clients: a user of the Aam Digital frontend app with the
 * [AamRealmRoles.ACCOUNT_MANAGER] role.
 *
 * An API client is never allowed, whatever roles its service account has,
 * so that the credentials of one integration cannot be used to create more of them.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize(
    "hasAuthority('${AamAuthorities.FRONTEND_CLIENT}') " +
        "and hasAuthority('${AamAuthorities.ROLE_PREFIX}${AamRealmRoles.ACCOUNT_MANAGER}')"
)
annotation class RequiresApiClientAdministrationAccess
