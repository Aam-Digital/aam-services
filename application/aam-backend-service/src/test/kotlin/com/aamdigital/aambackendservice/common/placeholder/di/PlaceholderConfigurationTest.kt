package com.aamdigital.aambackendservice.common.placeholder.di

import com.aamdigital.aambackendservice.common.keycloak.core.UserProfile
import com.aamdigital.aambackendservice.common.keycloak.core.UserProfileProvider
import com.aamdigital.aambackendservice.common.keycloak.di.AamKeycloakConfig
import com.aamdigital.aambackendservice.common.keycloak.di.KeycloakAdminConfiguration
import com.aamdigital.aambackendservice.common.placeholder.core.PlaceholderValueProvider
import com.aamdigital.aambackendservice.common.placeholder.core.Placeholders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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

    private val userProfileProvider =
        object : UserProfileProvider {
            override fun getUserProfile(userIdentifier: String) =
                UserProfile(userIdentifier, email = null, linkedEntityId = "User:alice")
        }

    private fun PlaceholderValueProvider.currentUserOf(userIdentifier: String) =
        getValues(userIdentifier, setOf(Placeholders.CURRENT_USER))

    @Test
    fun `should resolve placeholders from the user profile when there is a user profile provider`() {
        runner
            .withBean(UserProfileProvider::class.java, { userProfileProvider })
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context.getBean(PlaceholderValueProvider::class.java).currentUserOf("user-1"))
                    .isEqualTo(mapOf(Placeholders.CURRENT_USER to "User:alice"))
            }
    }

    @Test
    fun `should have no placeholder values without a user profile provider`() {
        runner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBean(PlaceholderValueProvider::class.java).currentUserOf("user-1")).isEmpty()
        }
    }

    // The user profile provider is looked up when the placeholder value provider is created, not by a
    // bean condition, which would depend on which configuration class is processed first.
    @Test
    fun `should provide the Keycloak user profile provider with Keycloak admin access, whatever the order`() {
        val orders =
            listOf(
                arrayOf(KeycloakAdminConfiguration::class.java, PlaceholderConfiguration::class.java),
                arrayOf(PlaceholderConfiguration::class.java, KeycloakAdminConfiguration::class.java)
            )

        orders.forEach { order ->
            ApplicationContextRunner()
                .withUserConfiguration(*order)
                .withPropertyValues("keycloak.server-url=https://keycloak.example.org")
                .withBean(AamKeycloakConfig::class.java, { keycloakConfig })
                .run { context ->
                    assertThat(context).hasNotFailed()
                    assertThat(context).hasSingleBean(UserProfileProvider::class.java)
                    assertThat(context).hasSingleBean(PlaceholderValueProvider::class.java)
                }
        }
    }
}
