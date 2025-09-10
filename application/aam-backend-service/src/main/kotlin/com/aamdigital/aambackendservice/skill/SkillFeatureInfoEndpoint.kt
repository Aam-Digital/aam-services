package com.aamdigital.aambackendservice.skill

import com.aamdigital.aambackendservice.common.actuator.FeatureRegistrar
import com.aamdigital.aambackendservice.common.actuator.FeaturesInfoDto
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "features.skill-api",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class SkillFeatureInfoEndpoint : FeatureRegistrar {
    override fun getFeatureInfo(): Pair<String, FeaturesInfoDto> {
        return "skill" to FeaturesInfoDto(true)
    }
}
