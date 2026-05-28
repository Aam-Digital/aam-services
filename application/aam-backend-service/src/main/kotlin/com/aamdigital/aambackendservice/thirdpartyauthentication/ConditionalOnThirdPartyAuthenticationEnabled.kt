package com.aamdigital.aambackendservice.thirdpartyauthentication

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/**
 * Activates the annotated bean only when the third-party-authentication feature flag is enabled.
 * Single source of truth for the `features.third-party-authentication.enabled` property.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(
    prefix = "features.third-party-authentication",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
annotation class ConditionalOnThirdPartyAuthenticationEnabled
