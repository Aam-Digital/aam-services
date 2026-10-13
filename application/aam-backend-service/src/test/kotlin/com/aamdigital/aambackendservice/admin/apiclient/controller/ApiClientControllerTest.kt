package com.aamdigital.aambackendservice.admin.apiclient.controller

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.aamdigital.aambackendservice.admin.apiclient.core.ApiClientAdministration
import com.aamdigital.aambackendservice.admin.apiclient.core.ApiClientAdministrationError
import com.aamdigital.aambackendservice.admin.apiclient.domain.ApiClientCredentials
import com.aamdigital.aambackendservice.common.error.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

class ApiClientControllerTest {
    companion object {
        private const val CLIENT_ID = "api-crm-x7k2p9"
        private const val SECRET = "client-secret"
    }

    private val apiClientAdministration = mock<ApiClientAdministration>()
    private val controller = ApiClientController(apiClientAdministration)

    private val user =
        JwtAuthenticationToken(
            Jwt
                .withTokenValue("token")
                .header("alg", "none")
                .subject("user-sub")
                .claim("email", "manager@example.org")
                .build()
        )

    private lateinit var logger: Logger
    private lateinit var logAppender: ListAppender<ILoggingEvent>

    @BeforeEach
    fun setUp() {
        logger = LoggerFactory.getLogger(ApiClientController::class.java) as Logger
        logAppender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(logAppender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(logAppender)
    }

    private fun auditLog(): ILoggingEvent = logAppender.list.single { it.formattedMessage.startsWith("[audit]") }

    @Test
    fun `should log who created which API client with which scopes, without its secret`() {
        // Given
        whenever(apiClientAdministration.createApiClient(any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(ApiClientCredentials(clientId = CLIENT_ID, clientSecret = SECRET))

        // When
        val response =
            controller.createApiClient(
                CreateApiClientRequestDto(name = "CRM", scopes = listOf("third_party_authentication")),
                user
            )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(auditLog().level).isEqualTo(Level.INFO)
        assertThat(auditLog().formattedMessage)
            .contains("manager@example.org", "user-sub", "created", CLIENT_ID, "third_party_authentication")
            .doesNotContain(SECRET)
    }

    @Test
    fun `should log who regenerated the secret of an API client, without the secret`() {
        // Given
        whenever(apiClientAdministration.regenerateSecret(CLIENT_ID))
            .thenReturn(ApiClientCredentials(clientId = CLIENT_ID, clientSecret = SECRET))

        // When
        controller.regenerateSecret(CLIENT_ID, user)

        // Then
        assertThat(auditLog().formattedMessage)
            .contains("manager@example.org", "regenerated the secret", CLIENT_ID)
            .doesNotContain(SECRET)
    }

    @Test
    fun `should log who deleted an API client`() {
        // When
        controller.deleteApiClient(CLIENT_ID, user)

        // Then
        assertThat(auditLog().formattedMessage).contains("manager@example.org", "deleted", CLIENT_ID)
    }

    @Test
    fun `should log who failed to change an API client`() {
        // Given
        doThrow(
            NotFoundException(
                message = "There is no API client '$CLIENT_ID'",
                code = ApiClientAdministrationError.API_CLIENT_NOT_FOUND
            )
        ).whenever(apiClientAdministration).deleteApiClient(CLIENT_ID)

        // When
        val response = controller.deleteApiClient(CLIENT_ID, user)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(auditLog().formattedMessage)
            .contains("manager@example.org", "could not delete", "API_CLIENT_NOT_FOUND")
    }
}
