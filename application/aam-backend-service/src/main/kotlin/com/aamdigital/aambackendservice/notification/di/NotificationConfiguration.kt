package com.aamdigital.aambackendservice.notification.di

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbInitializer
import com.aamdigital.aambackendservice.common.domain.ApplicationConfig
import com.aamdigital.aambackendservice.common.keycloak.di.AamKeycloakConfig
import com.aamdigital.aambackendservice.common.mail.MailSenderService
import com.aamdigital.aambackendservice.common.permission.core.PermissionCheckClient
import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationApiEnabled
import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationEmailEnabled
import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationFirebaseMode
import com.aamdigital.aambackendservice.notification.core.config.DefaultNotificationConfigCache
import com.aamdigital.aambackendservice.notification.core.config.NotificationConfigCache
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationHandler
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationUseCase
import com.aamdigital.aambackendservice.notification.core.create.DefaultCreateNotificationUseCase
import com.aamdigital.aambackendservice.notification.core.create.app.AppCreateNotificationHandler
import com.aamdigital.aambackendservice.notification.core.create.email.EmailCreateNotificationHandler
import com.aamdigital.aambackendservice.notification.core.create.email.KeycloakUserEmailProvider
import com.aamdigital.aambackendservice.notification.core.create.email.UserEmailProvider
import com.aamdigital.aambackendservice.notification.core.create.push.PushCreateNotificationHandler
import com.aamdigital.aambackendservice.notification.core.trigger.ApplyNotificationRulesUseCase
import com.aamdigital.aambackendservice.notification.core.trigger.DefaultApplyNotificationRulesUseCase
import com.aamdigital.aambackendservice.notification.queue.UserNotificationPublisher
import com.aamdigital.aambackendservice.notification.repository.UserDeviceRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.messaging.FirebaseMessaging
import org.keycloak.admin.client.Keycloak
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnNotificationApiEnabled
class NotificationConfiguration {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun notificationStartupDiagnostics(
        @Value("\${features.notification-api.email.enabled:false}") emailEnabled: Boolean,
        keycloakProvider: ObjectProvider<Keycloak>
    ): ApplicationRunner =
        ApplicationRunner {
            logger.info(
                "Notification startup diagnostics: emailFeatureEnabled={}, keycloakBeanAvailable={}",
                emailEnabled,
                keycloakProvider.ifAvailable != null
            )
        }

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
        permissionCheckClient: PermissionCheckClient,
        applicationConfig: ApplicationConfig,
        @Value("\${features.notification-api.email.enabled:false}") emailEnabled: Boolean
    ): ApplyNotificationRulesUseCase =
        DefaultApplyNotificationRulesUseCase(
            notificationConfigCache = notificationConfigCache,
            userNotificationPublisher = userNotificationPublisher,
            permissionCheckClient = permissionCheckClient,
            applicationConfig = applicationConfig,
            emailEnabled = emailEnabled
        )

    @Bean
    fun defaultCreateNotificationUseCase(
        createNotificationHandler: List<CreateNotificationHandler>
    ): CreateNotificationUseCase =
        DefaultCreateNotificationUseCase(
            createNotificationHandler = createNotificationHandler
        )

    @Bean("push-create-notification-handler")
    @ConditionalOnNotificationFirebaseMode
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

    @Bean("keycloak-user-email-provider")
    @ConditionalOnNotificationEmailEnabled
    @ConditionalOnBean(Keycloak::class)
    fun keycloakUserEmailProvider(
        keycloak: Keycloak,
        aamKeycloakConfig: AamKeycloakConfig
    ): UserEmailProvider =
        KeycloakUserEmailProvider(
            keycloak = keycloak,
            keycloakConfig = aamKeycloakConfig
        )

    @Bean("email-create-notification-handler")
    @ConditionalOnNotificationEmailEnabled
    @ConditionalOnBean(Keycloak::class)
    fun emailCreateNotificationHandler(
        mailSenderService: MailSenderService,
        userEmailProvider: UserEmailProvider,
        notificationEmailProperties: NotificationEmailProperties
    ): CreateNotificationHandler =
        EmailCreateNotificationHandler(
            mailSenderService = mailSenderService,
            userEmailProvider = userEmailProvider,
            notificationEmailProperties = notificationEmailProperties
        )
}
