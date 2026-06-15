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
 * Feature modules should implement FeatureRegistrar interface to provide their status.
 *
 * A registrar may use a dotted feature name (e.g. `notification.email`) to nest its status
 * as a sub-feature underneath the parent feature in the response, e.g.
 * `{ "notification": { "enabled": true, "email": { "enabled": true } } }`.
 *
 * see https://www.baeldung.com/spring-boot-actuators#custom-endpoint
 */
@Component
@Endpoint(id = "features")
class FeaturesEndpoint(
    private val featureRegistrars: List<FeatureRegistrar> = emptyList()
) {
    @ReadOperation
    fun getFeatureStatus(): Map<String, Any> {
        val root = mutableMapOf<String, Any>()
        featureRegistrars.forEach { registrar ->
            val (name, info) = registrar.getFeatureInfo()
            // Order-independent: ensures parent nodes exist regardless of registrar order
            val node = name.split(".").fold(root) { current, segment ->
                @Suppress("UNCHECKED_CAST")
                current.getOrPut(segment) { mutableMapOf<String, Any>() } as MutableMap<String, Any>
            }
            node["enabled"] = info.enabled
        }
        return root
    }
}
