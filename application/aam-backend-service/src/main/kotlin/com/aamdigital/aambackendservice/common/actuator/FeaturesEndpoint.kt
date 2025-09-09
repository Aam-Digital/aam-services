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
 * Feature modules should implement FeatureRegistrar interface to provide their status
 *
 * see https://www.baeldung.com/spring-boot-actuators#custom-endpoint
 */
@Component
@Endpoint(id = "features")
class FeaturesEndpoint(
    private val featureRegistrars: List<FeatureRegistrar> = emptyList()
) {

    @ReadOperation
    fun getFeatureStatus(): Map<String, FeaturesInfoDto> =
        featureRegistrars.associate { registrar ->
            registrar.getFeatureInfo()
        }
}
