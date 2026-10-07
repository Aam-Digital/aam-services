package com.aamdigital.aambackendservice.notification

import com.aamdigital.aambackendservice.common.actuator.FeatureRegistrar
import com.aamdigital.aambackendservice.common.actuator.FeaturesInfoDto
import org.springframework.stereotype.Component

/**
 * Reports `notification.email` in `/actuator/features` only while notification emails can actually
 * be sent, so the app offers the email option only when it works. It uses the same condition as the
 * email handler beans, which keeps the two in step.
 */
@Component
@ConditionalOnNotificationEmailConfigured
class NotificationEmailFeatureInfoEndpoint : FeatureRegistrar {
    override fun getFeatureInfo(): Pair<String, FeaturesInfoDto> = "notification.email" to FeaturesInfoDto(true)
}
