package com.aamdigital.aambackendservice.notification.di

import com.aamdigital.aambackendservice.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.notification.core.AppCreateNotificationHandler
import com.aamdigital.aambackendservice.notification.core.ApplyNotificationRulesUseCase
import com.aamdigital.aambackendservice.notification.core.CouchDbSyncNotificationConfigUseCase
import com.aamdigital.aambackendservice.notification.core.CreateNotificationHandler
import com.aamdigital.aambackendservice.notification.core.CreateNotificationUseCase
import com.aamdigital.aambackendservice.notification.core.DefaultApplyNotificationRulesUseCase
import com.aamdigital.aambackendservice.notification.core.DefaultCreateNotificationUseCase
import com.aamdigital.aambackendservice.notification.core.PushCreateNotificationHandler
import com.aamdigital.aambackendservice.notification.core.SyncNotificationConfigUseCase
import com.aamdigital.aambackendservice.notification.core.UserNotificationPublisher
import com.aamdigital.aambackendservice.notification.repositiory.NotificationConfigRepository
import com.aamdigital.aambackendservice.notification.repositiory.UserDeviceRepository
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(
    prefix = "features.notification-api",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class NotificationConfiguration {

    @Bean
    fun defaultSyncNotificationConfigUseCase(
        couchDbClient: CouchDbClient,
        notificationConfigRepository: NotificationConfigRepository,
    ): SyncNotificationConfigUseCase = CouchDbSyncNotificationConfigUseCase(
        couchDbClient = couchDbClient,
        notificationConfigRepository = notificationConfigRepository,
    )

    @Bean
    fun defaultApplyNotificationRulesUseCase(
        notificationConfigRepository: NotificationConfigRepository,
        userNotificationPublisher: UserNotificationPublisher,
    ): ApplyNotificationRulesUseCase = DefaultApplyNotificationRulesUseCase(
        notificationConfigRepository = notificationConfigRepository,
        userNotificationPublisher = userNotificationPublisher,
    )

    @Bean
    fun defaultCreateNotificationUseCase(
        createNotificationHandler: List<CreateNotificationHandler>
    ): CreateNotificationUseCase = DefaultCreateNotificationUseCase(
        createNotificationHandler = createNotificationHandler,
    )

    @Bean("push-create-notification-handler")
    fun pushCreateNotificationHandler(
        firebaseMessaging: FirebaseMessaging,
        userDeviceRepository: UserDeviceRepository,
        notificationConfigRepository: NotificationConfigRepository,
    ): CreateNotificationHandler = PushCreateNotificationHandler(
        firebaseMessaging = firebaseMessaging,
        userDeviceRepository = userDeviceRepository,
        notificationConfigRepository = notificationConfigRepository,
    )

    @Bean("app-create-notification-handler")
    fun appCreateNotificationHandler(
        couchDbClient: CouchDbClient,
        notificationConfigRepository: NotificationConfigRepository,
    ): CreateNotificationHandler = AppCreateNotificationHandler(
        couchDbClient = couchDbClient,
        notificationConfigRepository = notificationConfigRepository
    )
}
