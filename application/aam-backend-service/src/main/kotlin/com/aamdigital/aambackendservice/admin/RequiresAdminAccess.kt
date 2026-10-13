package com.aamdigital.aambackendservice.admin

import com.aamdigital.aambackendservice.common.security.AamAuthorities
import com.aamdigital.aambackendservice.common.security.AamRealmRoles
import org.springframework.security.access.prepost.PreAuthorize

/**
 * Access to the admin API: a user of the Aam Digital frontend app with both the
 * [AamRealmRoles.ACCOUNT_MANAGER] and the [AamRealmRoles.ADMIN] role.
 *
 * An API client is never allowed, whatever roles its service account has,
 * so that the credentials of one integration cannot be used to administer the system (e.g. create more of them).
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize(
    "hasAuthority('${AamAuthorities.FRONTEND_CLIENT}') " +
        "and hasAuthority('${AamAuthorities.ROLE_PREFIX}${AamRealmRoles.ACCOUNT_MANAGER}') " +
        "and hasAuthority('${AamAuthorities.ROLE_PREFIX}${AamRealmRoles.ADMIN}')"
)
annotation class RequiresAdminAccess
