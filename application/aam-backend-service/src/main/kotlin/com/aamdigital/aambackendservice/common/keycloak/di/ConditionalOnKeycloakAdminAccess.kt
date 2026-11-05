package com.aamdigital.aambackendservice.common.keycloak.di

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression

/**
 * Activates the annotated bean (a class or a `@Bean` method) only when the backend has admin access
 * to Keycloak (`keycloak.server-url` is set, see [AamKeycloakConfig]).
 *
 * An empty `KEYCLOAK_SERVERURL` (as in the env template) still creates the Keycloak bean, so the value is checked.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnExpression("!'\${keycloak.server-url:}'.isBlank()")
annotation class ConditionalOnKeycloakAdminAccess
