package com.aamdigital.aambackendservice.admin.apiclient.di

import com.aamdigital.aambackendservice.admin.apiclient.core.ApiClientAdministration
import com.aamdigital.aambackendservice.admin.apiclient.core.CreateApiClientUseCase
import com.aamdigital.aambackendservice.admin.apiclient.core.DefaultCreateApiClientUseCase
import com.aamdigital.aambackendservice.admin.apiclient.core.KeycloakApiClientAdministration
import com.aamdigital.aambackendservice.common.keycloak.core.ClientScopeRequest
import com.aamdigital.aambackendservice.common.keycloak.di.AamKeycloakConfig
import com.aamdigital.aambackendservice.common.keycloak.di.ConditionalOnKeycloakAdminAccess
import org.keycloak.admin.client.Keycloak
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnKeycloakAdminAccess
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
