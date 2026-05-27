package com.aamdigital.aambackendservice.thirdpartyauthentication

import com.aamdigital.aambackendservice.common.actuator.FeatureRegistrar
import com.aamdigital.aambackendservice.common.actuator.FeaturesInfoDto
import org.springframework.stereotype.Component

@Component
@ConditionalOnThirdPartyAuthenticationEnabled
class ThirdPartyAuthenticationFeatureInfoEndpoint : FeatureRegistrar {
    override fun getFeatureInfo(): Pair<String, FeaturesInfoDto> = "third-party-authentication" to FeaturesInfoDto(true)
}
