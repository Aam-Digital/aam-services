package com.aamdigital.aambackendservice.notification.di

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbInitializer
import com.aamdigital.aambackendservice.common.permission.core.PermissionCheckClient
import com.aamdigital.aambackendservice.notification.core.config.DefaultNotificationConfigCache
import com.aamdigital.aambackendservice.notification.core.config.NotificationConfigCache
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationHandler
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationUseCase
import com.aamdigital.aambackendservice.notification.core.create.DefaultCreateNotificationUseCase
import com.aamdigital.aambackendservice.notification.core.create.app.AppCreateNotificationHandler
import com.aamdigital.aambackendservice.notification.core.create.push.PushCreateNotificationHandler
import com.aamdigital.aambackendservice.notification.core.trigger.ApplyNotificationRulesUseCase
import com.aamdigital.aambackendservice.notification.core.trigger.DefaultApplyNotificationRulesUseCase
import com.aamdigital.aambackendservice.notification.queue.UserNotificationPublisher
import com.aamdigital.aambackendservice.notification.repository.UserDeviceRepository
import com.fasterxml.jackson.databind.ObjectMapper
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
    fun notificationConfigCache(
        couchDbClient: CouchDbClient,
        objectMapper: ObjectMapper
    ): NotificationConfigCache =
        DefaultNotificationConfigCache(
            couchDbClient = couchDbClient,
            objectMapper = objectMapper
        )

    @Bean
    fun defaultApplyNotificationRulesUseCase(
        notificationConfigCache: NotificationConfigCache,
        userNotificationPublisher: UserNotificationPublisher,
        permissionCheckClient: PermissionCheckClient
    ): ApplyNotificationRulesUseCase =
        DefaultApplyNotificationRulesUseCase(
            notificationConfigCache = notificationConfigCache,
            userNotificationPublisher = userNotificationPublisher,
            permissionCheckClient = permissionCheckClient
        )

    @Bean
    fun defaultCreateNotificationUseCase(
        createNotificationHandler: List<CreateNotificationHandler>
    ): CreateNotificationUseCase =
        DefaultCreateNotificationUseCase(
            createNotificationHandler = createNotificationHandler
        )

    @Bean("push-create-notification-handler")
    @ConditionalOnProperty(
        prefix = "features.notification-api",
        name = ["mode"],
        havingValue = "firebase",
        matchIfMissing = false
    )
    fun pushCreateNotificationHandler(
        firebaseMessaging: FirebaseMessaging,
        userDeviceRepository: UserDeviceRepository,
        notificationFirebaseClientConfiguration: NotificationFirebaseClientConfiguration
    ): PushCreateNotificationHandler =
        PushCreateNotificationHandler(
            firebaseMessaging = firebaseMessaging,
            userDeviceRepository = userDeviceRepository,
            notificationFirebaseClientConfiguration = notificationFirebaseClientConfiguration
        )

    @Bean("app-create-notification-handler")
    fun appCreateNotificationHandler(
        couchDbClient: CouchDbClient,
        couchDbInitializer: CouchDbInitializer
    ): CreateNotificationHandler =
        AppCreateNotificationHandler(
            couchDbClient = couchDbClient,
            couchDbInitializer = couchDbInitializer
        )
}
