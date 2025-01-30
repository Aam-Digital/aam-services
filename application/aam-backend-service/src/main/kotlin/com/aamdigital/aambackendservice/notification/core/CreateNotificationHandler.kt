package com.aamdigital.aambackendservice.notification.core

import com.aamdigital.aambackendservice.notification.core.event.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType

interface CreateNotificationHandler {
    fun canHandle(notificationChannelType: NotificationChannelType): Boolean
    fun createMessage(createUserNotificationEvent: CreateUserNotificationEvent): CreateNotificationData
}
