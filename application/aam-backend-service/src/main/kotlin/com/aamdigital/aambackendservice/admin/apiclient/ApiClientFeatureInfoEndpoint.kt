package com.aamdigital.aambackendservice.admin.apiclient

import com.aamdigital.aambackendservice.common.actuator.FeatureRegistrar
import com.aamdigital.aambackendservice.common.actuator.FeaturesInfoDto
import com.aamdigital.aambackendservice.common.keycloak.di.ConditionalOnKeycloakAdminAccess
import org.springframework.stereotype.Component

@Component
@ConditionalOnKeycloakAdminAccess
class ApiClientFeatureInfoEndpoint : FeatureRegistrar {
    override fun getFeatureInfo(): Pair<String, FeaturesInfoDto> = "admin.api-client" to FeaturesInfoDto(true)
}
