package com.aamdigital.aambackendservice.notification

import com.aamdigital.aambackendservice.actuator.FeaturesEndpoint
import com.aamdigital.aambackendservice.actuator.FeaturesInfoDto
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@EndpointWebExtension(endpoint = FeaturesEndpoint::class)
@ConditionalOnProperty(
    prefix = "features.notification-api",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class NotificationFeatureInfoEndpoint(
    private val featuresEndpoint: FeaturesEndpoint
) {
    @ReadOperation
    fun info(): WebEndpointResponse<MutableMap<String, FeaturesInfoDto>> {
        featuresEndpoint.features["notification"] = FeaturesInfoDto(true)

        return WebEndpointResponse(featuresEndpoint.features)
    }
}
