package com.aamdigital.aambackendservice.common.security

/**
 * Realm roles of the Aam Digital app that the backend checks (as `ROLE_<role>` authorities, see [AamAuthorities]).
 */
object AamRealmRoles {
    /** Users who manage the user accounts (and the API clients) of the system. */
    const val ACCOUNT_MANAGER = "account_manager"

    /** Administrators of the app's configuration. */
    const val ADMIN = "admin_app"
}
