package com.aamdigital.aambackendservice.notification.di

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.DatabaseRequest
import com.aamdigital.aambackendservice.common.domain.ApplicationConfig
import com.aamdigital.aambackendservice.common.keycloak.core.UserProfileProvider
import com.aamdigital.aambackendservice.common.mail.MailSenderService
import com.aamdigital.aambackendservice.common.outbox.Outbox
import com.aamdigital.aambackendservice.common.outbox.OutboxRetryPolicy
import com.aamdigital.aambackendservice.common.permission.core.PermissionCheckClient
import com.aamdigital.aambackendservice.common.placeholder.core.PlaceholderResolver
import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationApiEnabled
import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationEmailConfigured
import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationFirebaseMode
import com.aamdigital.aambackendservice.notification.NotificationEmailStartupDiagnostics
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
import com.aamdigital.aambackendservice.notification.core.config.DefaultNotificationConfigCache
import com.aamdigital.aambackendservice.notification.core.config.NotificationConfigCache
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationHandler
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationUseCase
import com.aamdigital.aambackendservice.notification.core.create.DefaultCreateNotificationUseCase
import com.aamdigital.aambackendservice.notification.core.create.app.AppCreateNotificationHandler
import com.aamdigital.aambackendservice.notification.core.create.email.EmailCreateNotificationHandler
import com.aamdigital.aambackendservice.notification.core.create.push.PushCreateNotificationHandler
import com.aamdigital.aambackendservice.notification.core.outbox.NotificationOutboxHandler
import com.aamdigital.aambackendservice.notification.core.outbox.OutboxUserNotificationPublisher
import com.aamdigital.aambackendservice.notification.core.outbox.UserNotificationPublisher
import com.aamdigital.aambackendservice.notification.core.trigger.ApplyNotificationRulesUseCase
import com.aamdigital.aambackendservice.notification.core.trigger.DefaultApplyNotificationRulesUseCase
import com.aamdigital.aambackendservice.notification.core.trigger.NotificationDocumentChangeHandler
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.aamdigital.aambackendservice.notification.repository.CouchDbUserDeviceRepository
import com.aamdigital.aambackendservice.notification.repository.UserDeviceRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.time.Duration

@Configuration
@ConditionalOnNotificationApiEnabled
class NotificationConfiguration {
    companion object {
        const val NOTIFICATION_OUTBOX_DATABASE = "notification-outbox"
    }

    @Bean
    fun notificationStartupDiagnostics(environment: Environment): ApplicationRunner =
        NotificationEmailStartupDiagnostics(environment)

    @Bean
    fun notificationConfigCache(
        couchDbClient: CouchDbClient,
        objectMapper: ObjectMapper,
        placeholderResolver: PlaceholderResolver,
        // reloading resolves placeholders again, e.g. after the entity linked to a user account changed
        @Value("\${notification.config-cache.ttl-millis:86400000}") ttlMillis: Long
    ): NotificationConfigCache =
        DefaultNotificationConfigCache(
            couchDbClient = couchDbClient,
            objectMapper = objectMapper,
            placeholderResolver = placeholderResolver,
            ttl = Duration.ofMillis(ttlMillis)
        )

    @Bean
    fun defaultApplyNotificationRulesUseCase(
        notificationConfigCache: NotificationConfigCache,
        userNotificationPublisher: UserNotificationPublisher,
        permissionCheckClient: PermissionCheckClient,
        applicationConfig: ApplicationConfig,
        createNotificationHandlers: List<CreateNotificationHandler>
    ): ApplyNotificationRulesUseCase {
        // Only emit a channel that a registered handler can deliver: an event without a handler turns
        // into an outbox entry that can never be delivered and is retried on every restart. Asking the
        // handlers keeps this in step with their bean conditions (email configuration, firebase mode
        // for push) instead of repeating those conditions here.
        fun handlerExistsFor(channel: NotificationChannelType) =
            createNotificationHandlers.any { handler -> handler.canHandle(channel) }

        return DefaultApplyNotificationRulesUseCase(
            notificationConfigCache = notificationConfigCache,
            userNotificationPublisher = userNotificationPublisher,
            permissionCheckClient = permissionCheckClient,
            applicationConfig = applicationConfig,
            emailEnabled = handlerExistsFor(NotificationChannelType.EMAIL),
            pushEnabled = handlerExistsFor(NotificationChannelType.PUSH)
        )
    }

    @Bean("notification-document-change-handler")
    fun notificationDocumentChangeHandler(
        notificationConfigCache: NotificationConfigCache,
        applyNotificationRulesUseCase: ApplyNotificationRulesUseCase
    ): NotificationDocumentChangeHandler =
        NotificationDocumentChangeHandler(
            notificationConfigCache = notificationConfigCache,
            applyNotificationRulesUseCase = applyNotificationRulesUseCase
        )

    @Bean("notification-outbox-database-request")
    fun notificationOutboxDatabaseRequest(): DatabaseRequest = DatabaseRequest(NOTIFICATION_OUTBOX_DATABASE)

    @Bean
    fun notificationOutbox(
        couchDbClient: CouchDbClient,
        objectMapper: ObjectMapper,
        notificationOutboxHandler: NotificationOutboxHandler,
        @Value("\${notification.outbox.max-attempts:3}") maxAttempts: Int,
        @Value("\${notification.outbox.retry-initial-interval-seconds:10}") retryInitialIntervalSeconds: Long,
        @Value("\${notification.outbox.retry-max-interval-seconds:60}") retryMaxIntervalSeconds: Long
    ): Outbox<CreateUserNotificationEvent> =
        Outbox(
            database = NOTIFICATION_OUTBOX_DATABASE,
            payloadType = CreateUserNotificationEvent::class,
            handler = notificationOutboxHandler,
            retryPolicy =
                OutboxRetryPolicy(
                    maxAttempts = maxAttempts,
                    initialInterval = Duration.ofSeconds(retryInitialIntervalSeconds),
                    maxInterval = Duration.ofSeconds(retryMaxIntervalSeconds)
                ),
            couchDbClient = couchDbClient,
            objectMapper = objectMapper
        )

    @Bean
    fun notificationOutboxHandler(createNotificationUseCase: CreateNotificationUseCase): NotificationOutboxHandler =
        NotificationOutboxHandler(createNotificationUseCase = createNotificationUseCase)

    @Bean
    fun outboxUserNotificationPublisher(
        notificationOutbox: Outbox<CreateUserNotificationEvent>
    ): UserNotificationPublisher = OutboxUserNotificationPublisher(notificationOutbox = notificationOutbox)

    @Bean
    fun defaultCreateNotificationUseCase(
        createNotificationHandler: List<CreateNotificationHandler>
    ): CreateNotificationUseCase =
        DefaultCreateNotificationUseCase(
            createNotificationHandler = createNotificationHandler
        )

    @Bean
    fun userDeviceRepository(couchDbClient: CouchDbClient): UserDeviceRepository =
        CouchDbUserDeviceRepository(couchDbClient = couchDbClient)

    @Bean("push-create-notification-handler")
    @ConditionalOnNotificationFirebaseMode
    fun pushCreateNotificationHandler(
        firebaseMessaging: FirebaseMessaging,
        userDeviceRepository: UserDeviceRepository,
        applicationConfig: ApplicationConfig
    ): PushCreateNotificationHandler =
        PushCreateNotificationHandler(
            firebaseMessaging = firebaseMessaging,
            userDeviceRepository = userDeviceRepository,
            applicationConfig = applicationConfig
        )

    @Bean("app-create-notification-handler")
    fun appCreateNotificationHandler(couchDbClient: CouchDbClient): CreateNotificationHandler =
        AppCreateNotificationHandler(couchDbClient = couchDbClient)

    // The email handler is gated on properties, including `keycloak.server-url`, the same condition that
    // gates the UserProfileProvider bean in KeycloakAdminConfiguration, rather than on
    // @ConditionalOnBean: the latter is order-sensitive on plain @Configuration classes and silently drops
    // the handler when NotificationConfiguration happens to be processed before KeycloakAdminConfiguration,
    // even when Keycloak is configured. NotificationEmailHandlerWiringTest covers both orders.
    @Bean("email-create-notification-handler")
    @ConditionalOnNotificationEmailConfigured
    fun emailCreateNotificationHandler(
        mailSenderService: MailSenderService,
        userProfileProvider: UserProfileProvider,
        notificationEmailProperties: NotificationEmailProperties
    ): CreateNotificationHandler =
        EmailCreateNotificationHandler(
            mailSenderService = mailSenderService,
            userProfileProvider = userProfileProvider,
            notificationEmailProperties = notificationEmailProperties
        )
}
