package com.aamdigital.aambackendservice.skill

import com.aamdigital.aambackendservice.common.actuator.FeatureRegistrar
import com.aamdigital.aambackendservice.common.actuator.FeaturesInfoDto
import org.springframework.stereotype.Component

@Component
@ConditionalOnSkillApiEnabled
class SkillFeatureInfoEndpoint : FeatureRegistrar {
    override fun getFeatureInfo(): Pair<String, FeaturesInfoDto> = "skill" to FeaturesInfoDto(true)
}
