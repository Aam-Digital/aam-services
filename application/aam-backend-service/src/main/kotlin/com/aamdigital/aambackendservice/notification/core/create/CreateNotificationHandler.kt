package com.aamdigital.aambackendservice.notification.core.create

import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType

interface CreateNotificationHandler {
    fun canHandle(notificationChannelType: NotificationChannelType): Boolean
    fun createMessage(createUserNotificationEvent: CreateUserNotificationEvent): CreateNotificationData
}
