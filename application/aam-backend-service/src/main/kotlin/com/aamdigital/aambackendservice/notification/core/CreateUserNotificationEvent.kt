package com.aamdigital.aambackendservice.notification.core

import com.aamdigital.aambackendservice.events.DomainEvent
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType

data class CreateUserNotificationEvent(
    val userIdentifier: String,
    val notificationChannelType: NotificationChannelType,
    val notificationRule: String,
) : DomainEvent()
