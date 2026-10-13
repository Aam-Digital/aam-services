package com.aamdigital.aambackendservice.apiclient.di

import com.aamdigital.aambackendservice.apiclient.ConditionalOnApiClientEnabled
import com.aamdigital.aambackendservice.apiclient.core.ApiClientAdministration
import com.aamdigital.aambackendservice.apiclient.core.CreateApiClientUseCase
import com.aamdigital.aambackendservice.apiclient.core.DefaultCreateApiClientUseCase
import com.aamdigital.aambackendservice.apiclient.core.KeycloakApiClientAdministration
import com.aamdigital.aambackendservice.common.keycloak.core.ClientScopeRequest
import com.aamdigital.aambackendservice.common.keycloak.di.AamKeycloakConfig
import org.keycloak.admin.client.Keycloak
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnApiClientEnabled
class ApiClientConfiguration {
    @Bean
    fun apiClientAdministration(
        keycloak: Keycloak,
        aamKeycloakConfig: AamKeycloakConfig
    ): ApiClientAdministration =
        KeycloakApiClientAdministration(
            keycloak = keycloak,
            realm = aamKeycloakConfig.realm,
            backendClientId = aamKeycloakConfig.clientId
        )

    @Bean
    fun createApiClientUseCase(
        apiClientAdministration: ApiClientAdministration,
        clientScopeRequests: ObjectProvider<ClientScopeRequest>
    ): CreateApiClientUseCase =
        DefaultCreateApiClientUseCase(
            administration = apiClientAdministration,
            clientScopeRequests = clientScopeRequests.orderedStream().toList()
        )
}
