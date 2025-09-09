package com.aamdigital.aambackendservice.skill

import com.aamdigital.aambackendservice.common.actuator.FeaturesEndpoint
import com.aamdigital.aambackendservice.common.actuator.FeaturesInfoDto
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@EndpointWebExtension(endpoint = FeaturesEndpoint::class)
@ConditionalOnProperty(
    prefix = "features.skill-api",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class SkillFeatureInfoEndpoint(
    private val featuresEndpoint: FeaturesEndpoint
) {
    @ReadOperation
    fun info(): WebEndpointResponse<MutableMap<String, FeaturesInfoDto>> {
        featuresEndpoint.features["skill"] = FeaturesInfoDto(true)

        return WebEndpointResponse(featuresEndpoint.features)
    }
}
