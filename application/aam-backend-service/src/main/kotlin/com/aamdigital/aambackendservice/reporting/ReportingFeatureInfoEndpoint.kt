package com.aamdigital.aambackendservice.reporting

import com.aamdigital.aambackendservice.common.actuator.FeatureRegistrar
import com.aamdigital.aambackendservice.common.actuator.FeaturesInfoDto
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "features.reporting",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class ReportingFeatureInfoEndpoint : FeatureRegistrar {
    override fun getFeatureInfo(): Pair<String, FeaturesInfoDto> = "reporting" to FeaturesInfoDto(true)
}
