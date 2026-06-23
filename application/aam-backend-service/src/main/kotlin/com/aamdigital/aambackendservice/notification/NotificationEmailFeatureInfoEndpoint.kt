package com.aamdigital.aambackendservice.notification

import com.aamdigital.aambackendservice.common.actuator.FeatureRegistrar
import com.aamdigital.aambackendservice.common.actuator.FeaturesInfoDto
import org.springframework.stereotype.Component

@Component
@ConditionalOnNotificationEmailEnabled
class NotificationEmailFeatureInfoEndpoint : FeatureRegistrar {
    override fun getFeatureInfo(): Pair<String, FeaturesInfoDto> =
        "notification.email" to FeaturesInfoDto(true)
}
