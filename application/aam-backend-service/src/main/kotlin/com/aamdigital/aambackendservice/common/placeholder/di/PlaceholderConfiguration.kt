package com.aamdigital.aambackendservice.common.placeholder.di

import com.aamdigital.aambackendservice.common.keycloak.di.AamKeycloakConfig
import com.aamdigital.aambackendservice.common.keycloak.di.ConditionalOnKeycloakAdminAccess
import com.aamdigital.aambackendservice.common.placeholder.core.KeycloakPlaceholderValueProvider
import com.aamdigital.aambackendservice.common.placeholder.core.PlaceholderValueProvider
import com.aamdigital.aambackendservice.common.placeholder.core.UnavailablePlaceholderValueProvider
import org.keycloak.admin.client.Keycloak
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Provides exactly one [PlaceholderValueProvider]: from Keycloak when admin access is configured,
 * otherwise one without any values.
 *
 * Both beans are gated on the same property expression rather than on `@ConditionalOnBean(Keycloak)`,
 * which is order-sensitive on plain `@Configuration` classes.
 */
@Configuration
class PlaceholderConfiguration {
    @Bean
    @ConditionalOnKeycloakAdminAccess
    fun keycloakPlaceholderValueProvider(
        keycloak: Keycloak,
        aamKeycloakConfig: AamKeycloakConfig
    ): PlaceholderValueProvider =
        KeycloakPlaceholderValueProvider(
            keycloak = keycloak,
            keycloakConfig = aamKeycloakConfig
        )

    @Bean
    @ConditionalOnExpression("'\${keycloak.server-url:}'.isBlank()")
    fun unavailablePlaceholderValueProvider(): PlaceholderValueProvider = UnavailablePlaceholderValueProvider()
}
