package com.aamdigital.aambackendservice.apiclient

import com.aamdigital.aambackendservice.common.actuator.FeatureRegistrar
import com.aamdigital.aambackendservice.common.actuator.FeaturesInfoDto
import org.springframework.stereotype.Component

@Component
@ConditionalOnApiClientEnabled
class ApiClientFeatureInfoEndpoint : FeatureRegistrar {
    override fun getFeatureInfo(): Pair<String, FeaturesInfoDto> = "api-client" to FeaturesInfoDto(true)
}
