package com.aamdigital.aambackendservice.thirdpartyauthentication.di

import com.aamdigital.aambackendservice.common.domain.ApplicationConfig
import com.aamdigital.aambackendservice.common.keycloak.di.AamKeycloakConfig
import jakarta.ws.rs.ProcessingException
import org.junit.jupiter.api.Test
import org.keycloak.admin.client.Keycloak
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.scheduling.TaskScheduler
import java.time.Instant

class ThirdPartyAuthenticationKeycloakConfigurationTest {
    private val keycloak = mock<Keycloak>()
    private val taskScheduler = mock<TaskScheduler>()

    private fun startup() =
        ThirdPartyAuthenticationKeycloakConfiguration()
            .thirdPartyAuthenticatorActivationStartup(
                keycloak = keycloak,
                aamKeycloakConfig =
                    AamKeycloakConfig(
                        serverUrl = "http://keycloak",
                        realm = "realm",
                        clientId = "aam-backend",
                        clientSecret = "secret"
                    ),
                applicationConfig = ApplicationConfig(baseUrl = "aam.example"),
                taskScheduler = taskScheduler
            )

    @Test
    fun `should try again in the background while Keycloak cannot be reached, up to the limit`() {
        // Given
        whenever(keycloak.realm(any())).thenThrow(ProcessingException("Connection refused"))

        // When the backend starts and every retry fails
        startup().run(DefaultApplicationArguments())
        val retries = argumentCaptor<Runnable>()
        repeat(ThirdPartyAuthenticationKeycloakConfiguration.MAX_RETRIES) { retry ->
            verify(taskScheduler, times(retry + 1)).schedule(retries.capture(), any<Instant>())
            retries.lastValue.run()
        }

        // Then it gives up
        verify(keycloak, times(ThirdPartyAuthenticationKeycloakConfiguration.MAX_RETRIES + 1)).realm(any())
        verify(taskScheduler, times(ThirdPartyAuthenticationKeycloakConfiguration.MAX_RETRIES))
            .schedule(any<Runnable>(), any<Instant>())
    }

    @Test
    fun `should not try again after a failure that trying again would not change`() {
        // Given
        whenever(keycloak.realm(any())).thenThrow(IllegalStateException("unexpected"))

        // When
        startup().run(DefaultApplicationArguments())

        // Then
        verify(taskScheduler, never()).schedule(any<Runnable>(), any<Instant>())
    }
}
