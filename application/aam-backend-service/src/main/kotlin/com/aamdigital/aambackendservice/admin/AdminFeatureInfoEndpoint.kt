package com.aamdigital.aambackendservice.admin

import com.aamdigital.aambackendservice.common.actuator.FeatureRegistrar
import com.aamdigital.aambackendservice.common.actuator.FeaturesInfoDto
import org.springframework.stereotype.Component

/**
 * The admin module is always enabled. Its parts that depend on other systems report their own status as
 * sub-features (e.g. `admin.api-client`).
 */
@Component
class AdminFeatureInfoEndpoint : FeatureRegistrar {
    override fun getFeatureInfo(): Pair<String, FeaturesInfoDto> = "admin" to FeaturesInfoDto(true)
}
