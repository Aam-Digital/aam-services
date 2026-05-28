package com.aamdigital.aambackendservice.notification

import com.aamdigital.aambackendservice.common.actuator.FeatureRegistrar
import com.aamdigital.aambackendservice.common.actuator.FeaturesInfoDto
import org.springframework.stereotype.Component

@Component
@ConditionalOnNotificationApiEnabled
class NotificationFeatureInfoEndpoint : FeatureRegistrar {
    override fun getFeatureInfo(): Pair<String, FeaturesInfoDto> = "notification" to FeaturesInfoDto(true)
}
