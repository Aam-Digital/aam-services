package com.aamdigital.aambackendservice.apiclient.core

import com.aamdigital.aambackendservice.apiclient.domain.ApiClient
import com.aamdigital.aambackendservice.apiclient.domain.ApiClientCredentials
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.keycloak.core.ClientScopeRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DefaultCreateApiClientUseCaseTest {
    /** Records the clients it is asked to create, so the tests check the outcome instead of individual calls. */
    private class InMemoryApiClientAdministration : ApiClientAdministration {
        val created = mutableListOf<NewApiClient>()
        var failure: Exception? = null

        override fun findApiClients(): List<ApiClient> = emptyList()

        override fun createApiClient(newApiClient: NewApiClient): ApiClientCredentials {
            failure?.let { throw it }
            created += newApiClient
            return ApiClientCredentials(clientId = newApiClient.clientId, clientSecret = "secret")
        }

        override fun regenerateSecret(clientId: String) = error("not used")

        override fun deleteApiClient(clientId: String) = error("not used")
    }

    private lateinit var administration: InMemoryApiClientAdministration
    private lateinit var useCase: DefaultCreateApiClientUseCase

    @BeforeEach
    fun setUp() {
        administration = InMemoryApiClientAdministration()
        useCase =
            DefaultCreateApiClientUseCase(
                administration = administration,
                clientScopeRequests =
                    listOf(
                        ClientScopeRequest(name = "reporting_read", description = "read reports"),
                        ClientScopeRequest(name = "third_party_authentication", description = "sign in users")
                    )
            )
    }

    private fun request(
        name: String = "TolaData Integration",
        scopes: Set<String> = setOf("reporting_read")
    ) = CreateApiClientRequest(
        name = name,
        scopes = scopes,
        createdBy = "manager@example.com"
    )

    @Test
    fun `should create an API client with a client id derived from the name`() {
        // When
        val outcome = useCase.run(request(name = "  Tolä Data: Integration  "))

        // Then
        assertThat(outcome).isInstanceOf(UseCaseOutcome.Success::class.java)
        val created = administration.created.single()
        assertThat(created.clientId).matches("api-tola-data-integration-[a-z0-9]{6}")
        assertThat(created.name).isEqualTo("Tolä Data: Integration")
        assertThat(created.scopes).containsExactly("reporting_read")
        assertThat(created.createdBy).isEqualTo("manager@example.com")

        val data = (outcome as UseCaseOutcome.Success).data
        assertThat(data.credentials.clientId).isEqualTo(created.clientId)
        assertThat(data.credentials.clientSecret).isEqualTo("secret")
    }

    @Test
    fun `should give each API client a different client id even if the names are the same`() {
        // When
        useCase.run(request(name = "Same"))
        useCase.run(request(name = "Same"))

        // Then
        assertThat(administration.created.map { it.clientId }.distinct()).hasSize(2)
    }

    @Test
    fun `should create a client id from the prefix and a random suffix for a name without latin letters`() {
        // When
        useCase.run(request(name = "डेटा"))

        // Then
        assertThat(administration.created.single().clientId).matches("api-[a-z0-9]{6}")
    }

    @Test
    fun `should reject a blank name`() {
        // When
        val outcome = useCase.run(request(name = "   "))

        // Then
        assertThat((outcome as UseCaseOutcome.Failure).errorCode).isEqualTo(CreateApiClientError.INVALID_NAME)
        assertThat(administration.created).isEmpty()
    }

    @Test
    fun `should reject a name that is too long`() {
        // When
        val outcome = useCase.run(request(name = "x".repeat(DefaultCreateApiClientUseCase.MAX_NAME_LENGTH + 1)))

        // Then
        assertThat((outcome as UseCaseOutcome.Failure).errorCode).isEqualTo(CreateApiClientError.INVALID_NAME)
    }

    @Test
    fun `should reject an API client without client scopes`() {
        // When
        val outcome = useCase.run(request(scopes = emptySet()))

        // Then
        assertThat((outcome as UseCaseOutcome.Failure).errorCode).isEqualTo(CreateApiClientError.NO_SCOPE)
        assertThat(administration.created).isEmpty()
    }

    @Test
    fun `should reject a client scope that no feature module offers to API clients`() {
        // Given a scope that exists in Keycloak, but grants nothing in this API
        val scopes = setOf("reporting_read", "roles")

        // When
        val outcome = useCase.run(request(scopes = scopes))

        // Then
        assertThat((outcome as UseCaseOutcome.Failure).errorCode).isEqualTo(CreateApiClientError.UNKNOWN_SCOPE)
        assertThat(outcome.errorMessage).contains("roles")
        assertThat(administration.created).isEmpty()
    }

    @Test
    fun `should create an API client with all the selected client scopes`() {
        // When
        val outcome = useCase.run(request(scopes = setOf("reporting_read", "third_party_authentication")))

        // Then
        assertThat(outcome).isInstanceOf(UseCaseOutcome.Success::class.java)
        assertThat(administration.created.single().scopes)
            .containsExactlyInAnyOrder("reporting_read", "third_party_authentication")
    }

    @Test
    fun `should return the error code of a failure in Keycloak`() {
        // Given
        administration.failure =
            ExternalSystemException(
                message = "Keycloak unavailable",
                code = ApiClientAdministrationError.KEYCLOAK_REQUEST_FAILED
            )

        // When
        val outcome = useCase.run(request())

        // Then
        assertThat((outcome as UseCaseOutcome.Failure).errorCode)
            .isEqualTo(ApiClientAdministrationError.KEYCLOAK_REQUEST_FAILED)
    }
}
