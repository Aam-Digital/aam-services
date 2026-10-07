package com.aamdigital.aambackendservice.notification.di

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.aamdigital.aambackendservice.common.actuator.FeatureRegistrar
import com.aamdigital.aambackendservice.common.actuator.FeaturesEndpoint
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.domain.ApplicationConfig
import com.aamdigital.aambackendservice.common.keycloak.di.AamKeycloakConfig
import com.aamdigital.aambackendservice.common.keycloak.di.KeycloakAdminConfiguration
import com.aamdigital.aambackendservice.common.mail.MailSenderService
import com.aamdigital.aambackendservice.common.permission.core.PermissionCheckClient
import com.aamdigital.aambackendservice.notification.NotificationEmailFeatureInfoEndpoint
import com.aamdigital.aambackendservice.notification.NotificationEmailStartupDiagnostics
import com.aamdigital.aambackendservice.notification.NotificationFeatureInfoEndpoint
import com.aamdigital.aambackendservice.notification.core.create.CreateNotificationHandler
import com.aamdigital.aambackendservice.notification.domain.NotificationChannelType
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.keycloak.admin.client.Keycloak
import org.mockito.kotlin.mock
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender
import ch.qos.logback.classic.Logger as LogbackLogger

/**
 * Wires the notification module the way the application does and checks when the email channel
 * exists: only when the notification API is on and the SMTP host, the sender address and the
 * Keycloak access are all set to a non-blank value. There is no flag for it.
 *
 * Whatever the settings are, the context has to start, because email is an optional channel.
 */
class NotificationEmailHandlerWiringTest {
    /**
     * Picks up [NotificationEmailProperties] and [AamKeycloakConfig] the way the application's
     * `@ConfigurationPropertiesScan` does, including their own bean conditions.
     */
    @Configuration
    @ConfigurationPropertiesScan(basePackageClasses = [NotificationEmailProperties::class, AamKeycloakConfig::class])
    class PropertiesScanConfiguration

    private val runner =
        runnerWith(
            NotificationConfiguration::class.java,
            NotificationFeatureInfoEndpoint::class.java,
            NotificationEmailFeatureInfoEndpoint::class.java
        ).withBean(Keycloak::class.java, { mock<Keycloak>() })

    private val configured =
        arrayOf(
            "features.notification-api.enabled=true",
            "spring.mail.host=smtp.example.org",
            "notification.email.from=notifications@example.org",
            "keycloak.server-url=https://keycloak.example.org",
            "keycloak.realm=realm",
            "keycloak.client-id=client",
            "keycloak.client-secret=secret"
        )

    /** Keycloak settings the way the deployment template ships them: present, but empty. */
    private val emptyKeycloakSettings =
        arrayOf(
            "keycloak.server-url=",
            "keycloak.realm=",
            "keycloak.client-id=",
            "keycloak.client-secret="
        )

    /** The configuration classes the email channel lives in, plus what they get from the rest of the application. */
    private fun runnerWith(vararg configurations: Class<*>) =
        ApplicationContextRunner()
            .withUserConfiguration(
                PropertiesScanConfiguration::class.java,
                MailConfiguration::class.java,
                NotificationEmailCacheConfiguration::class.java,
                *configurations
            ).withBean(CouchDbClient::class.java, { mock<CouchDbClient>() })
            .withBean(ObjectMapper::class.java, { ObjectMapper() })
            .withBean(PermissionCheckClient::class.java, { PermissionCheckClient() })
            .withBean(ApplicationConfig::class.java, { ApplicationConfig("aam.example.org") })
            // what Spring Boot's mail auto-configuration provides once spring.mail.host is set
            .withBean(JavaMailSender::class.java, { mock<JavaMailSender>() })

    private fun ApplicationContextRunner.withSettings(vararg overrides: String) =
        withPropertyValues(*configured, *overrides)

    private fun AssertableApplicationContext.canHandleEmail() =
        getBeansOfType(CreateNotificationHandler::class.java)
            .values
            .any { it.canHandle(NotificationChannelType.EMAIL) }

    /** What `/actuator/features` answers. */
    private fun AssertableApplicationContext.features() =
        FeaturesEndpoint(getBeansOfType(FeatureRegistrar::class.java).values.toList()).getFeatureStatus()

    private fun AssertableApplicationContext.assertEmailChannelIsOn() {
        assertThat(this).hasNotFailed()
        assertThat(this).hasBean("email-create-notification-handler")
        assertThat(this).hasBean("keycloak-user-email-provider")
        assertThat(this).hasSingleBean(MailSenderService::class.java)
        assertThat(this).hasSingleBean(NotificationEmailProperties::class.java)
        assertThat(this).hasBean("notificationEmailCacheManager")
        assertThat(canHandleEmail()).isTrue()
        assertThat(features()).isEqualTo(
            mapOf("notification" to mapOf("enabled" to true, "email" to mapOf("enabled" to true)))
        )
    }

    private fun AssertableApplicationContext.assertEmailChannelIsOff() {
        assertThat(this).hasNotFailed()
        // the rest of the notification module is not affected
        assertThat(this).hasBean("app-create-notification-handler")
        assertThat(this).hasBean("defaultApplyNotificationRulesUseCase")
        assertThat(this).doesNotHaveBean("email-create-notification-handler")
        assertThat(this).doesNotHaveBean("keycloak-user-email-provider")
        assertThat(this).doesNotHaveBean(MailSenderService::class.java)
        assertThat(this).doesNotHaveBean(NotificationEmailProperties::class.java)
        assertThat(this).doesNotHaveBean(CacheManager::class.java)
        assertThat(canHandleEmail()).isFalse()
        assertThat(features()).isEqualTo(mapOf("notification" to mapOf("enabled" to true)))
    }

    @Test
    fun `should have the email channel when the API is enabled and SMTP, sender address and Keycloak are set`() {
        runner.withSettings().run { it.assertEmailChannelIsOn() }
    }

    @Test
    fun `should have the email channel when the settings come from environment variables`() {
        // the way a deployment sets them, including KEYCLOAK_SERVERURL for keycloak.server-url
        runner
            .withInitializer { context ->
                TestPropertyValues
                    .of(
                        "FEATURES_NOTIFICATIONAPI_ENABLED=true",
                        "SPRING_MAIL_HOST=smtp.example.org",
                        "NOTIFICATION_EMAIL_FROM=notifications@example.org",
                        "KEYCLOAK_SERVERURL=https://keycloak.example.org",
                        "KEYCLOAK_REALM=realm",
                        "KEYCLOAK_CLIENTID=client",
                        "KEYCLOAK_CLIENTSECRET=secret"
                    ).applyTo(context.environment, TestPropertyValues.Type.SYSTEM_ENVIRONMENT)
                ConfigurationPropertySources.attach(context.environment)
            }.run { it.assertEmailChannelIsOn() }
    }

    @Test
    fun `should start without the email channel when the SMTP host is an empty string`() {
        // SPRING_MAIL_HOST= is what the deployment template ships
        runner.withSettings("spring.mail.host=").run { it.assertEmailChannelIsOff() }
    }

    @Test
    fun `should start without the email channel when the SMTP host is not set at all`() {
        runner
            .withPropertyValues(
                "features.notification-api.enabled=true",
                "notification.email.from=notifications@example.org",
                "keycloak.server-url=https://keycloak.example.org",
                "keycloak.realm=realm",
                "keycloak.client-id=client",
                "keycloak.client-secret=secret"
            ).run { it.assertEmailChannelIsOff() }
    }

    @Test
    fun `should start without the email channel when the sender address is missing`() {
        runner.withSettings("notification.email.from=").run { it.assertEmailChannelIsOff() }
    }

    @Test
    fun `should start without the email channel when the Keycloak settings are empty`() {
        // KEYCLOAK_SERVERURL= and the like are what the deployment template ships
        runner.withSettings(*emptyKeycloakSettings).run { it.assertEmailChannelIsOff() }
    }

    @Test
    fun `should start without the email channel when there are no Keycloak settings at all`() {
        runner
            .withPropertyValues(
                "features.notification-api.enabled=true",
                "spring.mail.host=smtp.example.org",
                "notification.email.from=notifications@example.org"
            ).run { it.assertEmailChannelIsOff() }
    }

    @Test
    fun `should have no email beans when the notification API is disabled`() {
        runner.withSettings("features.notification-api.enabled=false").run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(NotificationConfiguration::class.java)
            assertThat(context).doesNotHaveBean(MailSenderService::class.java)
            assertThat(context).doesNotHaveBean(NotificationEmailProperties::class.java)
            assertThat(context).doesNotHaveBean(CacheManager::class.java)
            assertThat(context.features()).isEmpty()
        }
    }

    @Test
    fun `should ignore the removed flag when it is false although SMTP is configured`() {
        runner.withSettings("features.notification-api.email.enabled=false").run { it.assertEmailChannelIsOn() }
    }

    @Test
    fun `should start without the email channel when the removed flag is true but SMTP is not configured`() {
        // this used to be a startup failure: the flag required a MailSenderService that did not exist
        runner
            .withSettings("features.notification-api.email.enabled=true", "spring.mail.host=")
            .run { it.assertEmailChannelIsOff() }
    }

    @Test
    fun `should warn at startup that the removed flag is still set`() {
        // Given
        val logger = LoggerFactory.getLogger(NotificationEmailStartupDiagnostics::class.java) as LogbackLogger
        val logAppender = ListAppender<ILoggingEvent>().apply { start() }
        // the application.yaml default is WARN, and an earlier Spring test in the same JVM leaves it applied
        val previousLevel = logger.level
        logger.level = Level.INFO
        logger.addAppender(logAppender)

        try {
            // When
            runner.withSettings("features.notification-api.email.enabled=false").run { context ->
                context
                    .getBean("notificationStartupDiagnostics", ApplicationRunner::class.java)
                    .run(DefaultApplicationArguments())
            }

            // Then
            val warnings = logAppender.list.filter { it.level == Level.WARN }
            assertThat(warnings).hasSize(1)
            assertThat(warnings.single().formattedMessage).contains("features.notification-api.email.enabled")
        } finally {
            logger.detachAppender(logAppender)
            logger.level = previousLevel
        }
    }

    // The email beans are gated on properties rather than on @ConditionalOnBean(Keycloak), which would
    // drop them depending on which configuration class is processed first.
    @Test
    fun `should have the email channel whichever configuration is processed first`() {
        val orders =
            listOf(
                arrayOf(KeycloakAdminConfiguration::class.java, NotificationConfiguration::class.java),
                arrayOf(NotificationConfiguration::class.java, KeycloakAdminConfiguration::class.java)
            )

        orders.forEach { order ->
            runnerWith(*order).withSettings().run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasBean("email-create-notification-handler")
                assertThat(context.canHandleEmail()).isTrue()
            }
        }
    }
}
