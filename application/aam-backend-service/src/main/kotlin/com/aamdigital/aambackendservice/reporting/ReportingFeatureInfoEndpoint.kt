package com.aamdigital.aambackendservice.reporting

import com.aamdigital.aambackendservice.common.actuator.FeatureRegistrar
import com.aamdigital.aambackendservice.common.actuator.FeaturesInfoDto
import org.springframework.stereotype.Component

@Component
@ConditionalOnReportingEnabled
class ReportingFeatureInfoEndpoint : FeatureRegistrar {
    override fun getFeatureInfo(): Pair<String, FeaturesInfoDto> = "reporting" to FeaturesInfoDto(true)
}
