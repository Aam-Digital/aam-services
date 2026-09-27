package com.aamdigital.aambackendservice.notification.di

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbInitializer
import com.aamdigital.aambackendservice.common.couchdb.core.DatabaseRequest
import com.aamdigital.aambackendservice.common.domain.ApplicationConfig
import com.aamdigital.aambackendservice.common.keycloak.di.AamKeycloakConfig
import com.aamdigital.aambackendservice.common.mail.MailSenderService
import com.aamdigital.aambackendservice.common.outbox.Outbox
import com.aamdigital.aambackendservice.common.outbox.OutboxDrainer
import com.aamdigital.aambackendservice.common.outbox.OutboxRetryPolicy
import com.aamdigital.aambackendservice.common.permission.core.PermissionCheckClient
import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationApiEnabled
import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationEmailEnabled
import com.aamdigital.aambackendservice.notification.ConditionalOnNotificationFirebaseMode
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent
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
import org.keycloak.admin.client.Keycloak
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@ConditionalOnNotificationApiEnabled
class NotificationConfiguration {
    companion object {
        const val NOTIFICATION_OUTBOX_DATABASE = "notification-outbox"
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun notificationStartupDiagnostics(
        @Value("\${features.notification-api.email.enabled:false}") emailEnabled: Boolean,
        @Value("\${spring.mail.host:}") mailHost: String,
        keycloakProvider: ObjectProvider<Keycloak>
    ): ApplicationRunner =
        ApplicationRunner {
            val keycloakAvailable = keycloakProvider.ifAvailable != null
            val mailHostConfigured = mailHost.isNotBlank()
            if (emailEnabled && !keycloakAvailable) {
                logger.error(
                    "Notification email is ENABLED (features.notification-api.email.enabled=true) but Keycloak is " +
                            "not configured (keycloak.server-url unset), so no email handler exists and email " +
                            "notifications will be skipped. Set keycloak.server-url (+ realm/client-id/client-secret) " +
                            "and spring.mail.host to enable them."
                )
            } else {
                logger.info(
                    "Notification startup diagnostics: emailFeatureEnabled={}, keycloakBeanAvailable={}, " +
                            "mailHostConfigured={}",
                    emailEnabled,
                    keycloakAvailable,
                    mailHostConfigured
                )
            }
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
        createNotificationHandlers: List<CreateNotificationHandler>
    ): ApplyNotificationRulesUseCase {
        // Only emit a channel that a registered handler can deliver: an event without a handler turns
        // into an outbox entry that can never be delivered and is retried on every restart. Asking the
        // handlers keeps this in step with their bean conditions (email feature flag plus Keycloak,
        // firebase mode for push) instead of repeating those conditions here.
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
        couchDbInitializer: CouchDbInitializer,
        objectMapper: ObjectMapper
    ): Outbox<CreateUserNotificationEvent> =
        Outbox(
            database = NOTIFICATION_OUTBOX_DATABASE,
            payloadType = CreateUserNotificationEvent::class,
            couchDbClient = couchDbClient,
            couchDbInitializer = couchDbInitializer,
            objectMapper = objectMapper
        )

    @Bean
    fun notificationOutboxHandler(createNotificationUseCase: CreateNotificationUseCase): NotificationOutboxHandler =
        NotificationOutboxHandler(createNotificationUseCase = createNotificationUseCase)

    @Bean
    fun outboxUserNotificationPublisher(
        notificationOutbox: Outbox<CreateUserNotificationEvent>,
        notificationOutboxHandler: NotificationOutboxHandler
    ): UserNotificationPublisher =
        OutboxUserNotificationPublisher(
            notificationOutbox = notificationOutbox,
            notificationOutboxHandler = notificationOutboxHandler
        )

    @Bean
    fun notificationOutboxDrainer(
        notificationOutbox: Outbox<CreateUserNotificationEvent>,
        notificationOutboxHandler: NotificationOutboxHandler,
        @Value("\${notification.outbox.max-attempts:3}") maxAttempts: Int,
        @Value("\${notification.outbox.retry-initial-interval-seconds:10}") retryInitialIntervalSeconds: Long,
        @Value("\${notification.outbox.retry-max-interval-seconds:60}") retryMaxIntervalSeconds: Long
    ): OutboxDrainer<CreateUserNotificationEvent> =
        OutboxDrainer(
            outbox = notificationOutbox,
            handler = notificationOutboxHandler,
            retryPolicy =
                OutboxRetryPolicy(
                    maxAttempts = maxAttempts,
                    initialInterval = Duration.ofSeconds(retryInitialIntervalSeconds),
                    maxInterval = Duration.ofSeconds(retryMaxIntervalSeconds)
                )
        )

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
    fun appCreateNotificationHandler(
        couchDbClient: CouchDbClient,
        couchDbInitializer: CouchDbInitializer
    ): CreateNotificationHandler =
        AppCreateNotificationHandler(
            couchDbClient = couchDbClient,
            couchDbInitializer = couchDbInitializer
        )

    // Gated on the `keycloak.server-url` property (the same condition that gates the Keycloak bean in
    // KeycloakAdminConfiguration) rather than @ConditionalOnBean(Keycloak): the latter is order-sensitive
    // on plain @Configuration classes and silently drops this bean when NotificationConfiguration happens
    // to be processed before KeycloakAdminConfiguration — even when Keycloak is configured. See
    // NotificationEmailHandlerWiringTest, which fails the "notification config first" case under @ConditionalOnBean.
    @Bean("keycloak-user-email-provider")
    @ConditionalOnNotificationEmailEnabled
    @ConditionalOnProperty(prefix = "keycloak", name = ["server-url"])
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
    @ConditionalOnProperty(prefix = "keycloak", name = ["server-url"])
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
