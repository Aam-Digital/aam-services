package com.aamdigital.aambackendservice.thirdpartyauthentication.di

import com.aamdigital.aambackendservice.common.keycloak.di.AamKeycloakConfig
import com.aamdigital.aambackendservice.thirdpartyauthentication.ConditionalOnThirdPartyAuthenticationEnabled
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.AamKeycloakAuthenticationProvider
import com.aamdigital.aambackendservice.thirdpartyauthentication.core.AuthenticationProvider
import org.keycloak.admin.client.Keycloak
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnThirdPartyAuthenticationEnabled
class ThirdPartyAuthenticationKeycloakConfiguration {
    @Bean
    fun aamKeycloakAuthenticatorProvider(
        keycloak: Keycloak,
        aamKeycloakConfig: AamKeycloakConfig
    ): AuthenticationProvider =
        AamKeycloakAuthenticationProvider(
            keycloak = keycloak,
            keycloakConfig = aamKeycloakConfig
        )
}
