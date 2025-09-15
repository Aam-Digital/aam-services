package com.aamdigital.aambackendservice.thirdpartyauthentication

import com.aamdigital.aambackendservice.common.actuator.FeatureRegistrar
import com.aamdigital.aambackendservice.common.actuator.FeaturesInfoDto
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "features.third-party-authentication-api",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class ThirdPartyAuthenticationFeatureInfoEndpoint : FeatureRegistrar {
    override fun getFeatureInfo(): Pair<String, FeaturesInfoDto> {
        return "third-party-authentication" to FeaturesInfoDto(true)
    }
}
