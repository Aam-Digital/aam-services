package com.aamdigital.aambackendservice.notification.di

import com.aamdigital.aambackendservice.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.notification.core.config.CouchDbSyncNotificationConfigUseCase
import com.aamdigital.aambackendservice.notification.core.config.SyncNotificationConfigUseCase
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationHandler
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationUseCase
import com.aamdigital.aambackendservice.notification.core.create.DefaultCreateNotificationUseCase
import com.aamdigital.aambackendservice.notification.core.create.app.AppCreateNotificationHandler
import com.aamdigital.aambackendservice.notification.core.create.push.PushCreateNotificationHandler
import com.aamdigital.aambackendservice.notification.core.trigger.ApplyNotificationRulesUseCase
import com.aamdigital.aambackendservice.notification.core.trigger.DefaultApplyNotificationRulesUseCase
import com.aamdigital.aambackendservice.notification.queue.UserNotificationPublisher
import com.aamdigital.aambackendservice.notification.repository.NotificationConfigRepository
import com.aamdigital.aambackendservice.notification.repository.UserDeviceRepository
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
        notificationFirebaseClientConfiguration: NotificationFirebaseClientConfiguration,
    ): CreateNotificationHandler = PushCreateNotificationHandler(
        firebaseMessaging = firebaseMessaging,
        userDeviceRepository = userDeviceRepository,
        notificationFirebaseClientConfiguration = notificationFirebaseClientConfiguration
    )

    @Bean("app-create-notification-handler")
    fun appCreateNotificationHandler(
        couchDbClient: CouchDbClient
    ): CreateNotificationHandler = AppCreateNotificationHandler(
        couchDbClient = couchDbClient
    )
}
