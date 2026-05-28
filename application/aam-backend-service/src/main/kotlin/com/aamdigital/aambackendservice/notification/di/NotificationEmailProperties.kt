package com.aamdigital.aambackendservice.notification.di

import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationEmailEnabled
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("notification.email")
@ConditionalOnNotificationEmailEnabled
data class NotificationEmailProperties(
    val from: String = "",
    val subjectPrefix: String = "Aam Digital",
    val manageSettingsUrl: String = ""
)
