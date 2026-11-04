package com.aamdigital.aambackendservice.common.placeholder.di

import com.aamdigital.aambackendservice.common.keycloak.di.AamKeycloakConfig
import com.aamdigital.aambackendservice.common.placeholder.core.KeycloakPlaceholderValueProvider
import com.aamdigital.aambackendservice.common.placeholder.core.PlaceholderValueProvider
import com.aamdigital.aambackendservice.common.placeholder.core.UnavailablePlaceholderValueProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.keycloak.admin.client.Keycloak
import org.mockito.kotlin.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class PlaceholderConfigurationTest {
    private val runner =
        ApplicationContextRunner()
            .withUserConfiguration(PlaceholderConfiguration::class.java)

    private val keycloakConfig =
        AamKeycloakConfig(
            serverUrl = "https://keycloak.example.org",
            realm = "realm",
            clientId = "client",
            clientSecret = "secret"
        )

    @Test
    fun `should look up placeholder values in Keycloak when admin access is configured`() {
        runner
            .withPropertyValues("keycloak.server-url=https://keycloak.example.org")
            .withBean(Keycloak::class.java, { mock<Keycloak>() })
            .withBean(AamKeycloakConfig::class.java, { keycloakConfig })
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context)
                    .getBean(PlaceholderValueProvider::class.java)
                    .isInstanceOf(KeycloakPlaceholderValueProvider::class.java)
            }
    }

    @Test
    fun `should have no placeholder values when Keycloak is not configured or its url is empty`() {
        listOf(runner, runner.withPropertyValues("keycloak.server-url=")).forEach { contextRunner ->
            contextRunner.run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context)
                    .getBean(PlaceholderValueProvider::class.java)
                    .isInstanceOf(UnavailablePlaceholderValueProvider::class.java)
            }
        }
    }
}
