package com.aamdigital.aambackendservice.common.actuator

import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.stereotype.Component

data class FeaturesInfoDto(
    val enabled: Boolean
)

/**
 * Central Health Endpoint for all features.
 *
 * Extend this in feature modules to register their status (see example in NotificationFeatureInfoEndpoint)
 *
 * see https://www.baeldung.com/spring-boot-actuators#custom-endpoint
 */
@Component
@Endpoint(id = "features")
class FeaturesEndpoint {

    val features = mutableMapOf<String, FeaturesInfoDto>()

    @ReadOperation
    fun getFeatureStatus(): MutableMap<String, FeaturesInfoDto> {
        return features
    }
}
