package com.aamdigital.aambackendservice.notification.core.create.email

import com.aamdigital.aambackendservice.common.keycloak.di.AamKeycloakConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.admin.client.resource.UsersResource
import org.keycloak.representations.idm.UserRepresentation
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class KeycloakUserEmailProviderTest {
    private lateinit var provider: KeycloakUserEmailProvider

    @Mock
    lateinit var keycloak: Keycloak

    @Mock
    lateinit var realmResource: RealmResource

    @Mock
    lateinit var usersResource: UsersResource

    @Mock
    lateinit var userResource: UserResource

    private val config =
        AamKeycloakConfig(
            serverUrl = "https://keycloak.test",
            realm = "test-realm",
            clientId = "backend",
            clientSecret = "secret"
        )

    @BeforeEach
    fun setUp() {
        provider = KeycloakUserEmailProvider(keycloak = keycloak, keycloakConfig = config)
        whenever(keycloak.realm(any())).thenReturn(realmResource)
        whenever(realmResource.users()).thenReturn(usersResource)
        whenever(usersResource.get(any())).thenReturn(userResource)
    }

    @Test
    fun `should return email when user is found in Keycloak`() {
        // Given
        val representation = UserRepresentation().apply { email = "user@example.com" }
        whenever(userResource.toRepresentation()).thenReturn(representation)

        // When
        val result = provider.lookupEmail("user-uuid-123")

        // Then
        assertThat(result).isEqualTo("user@example.com")
    }

    @Test
    fun `should return null when user has no representation in Keycloak`() {
        // Given
        whenever(userResource.toRepresentation()).thenReturn(null)

        // When
        val result = provider.lookupEmail("user-uuid-123")

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `should return null when user exists in Keycloak but has no email`() {
        // Given
        val representation = UserRepresentation().apply { email = null }
        whenever(userResource.toRepresentation()).thenReturn(representation)

        // When
        val result = provider.lookupEmail("user-uuid-123")

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `should return null and log warning when Keycloak throws exception`() {
        // Given
        whenever(userResource.toRepresentation()).thenThrow(RuntimeException("Keycloak unavailable"))

        // When
        val result = provider.lookupEmail("user-uuid-123")

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `should use UUID-based lookup not username search`() {
        // Given
        val representation = UserRepresentation().apply { email = "user@example.com" }
        whenever(userResource.toRepresentation()).thenReturn(representation)

        // When
        provider.lookupEmail("user-uuid-123")

        // Then
        verify(usersResource).get("user-uuid-123")
        verify(usersResource, times(0)).searchByUsername(any(), any())
    }
}
