package com.aamdigital.aambackendservice.notification.core

import com.aamdigital.aambackendservice.events.DomainEvent
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.domain.NotificationDetails

data class CreateUserNotificationEvent(
    val userIdentifier: String,
    val notificationChannelType: NotificationChannelType,
    val notificationRule: String,
    val details: NotificationDetails,
) : DomainEvent()
