package com.aamdigital.aambackendservice.apiclient

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/**
 * Activates the annotated bean only when the api-client feature flag is enabled
 * and the backend has admin access to Keycloak, which manages the API clients.
 * Single source of truth for the `features.api-client.enabled` property.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(
    prefix = "features.api-client",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
// an empty KEYCLOAK_SERVERURL (as in the env template) still creates the Keycloak bean, so check the value
@ConditionalOnExpression("!'\${keycloak.server-url:}'.isBlank()")
annotation class ConditionalOnApiClientEnabled
