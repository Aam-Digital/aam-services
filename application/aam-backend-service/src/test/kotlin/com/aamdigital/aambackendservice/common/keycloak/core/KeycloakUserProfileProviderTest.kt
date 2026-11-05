package com.aamdigital.aambackendservice.common.keycloak.core

import com.aamdigital.aambackendservice.common.keycloak.di.AamKeycloakConfig
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.ProcessingException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class KeycloakUserProfileProviderTest {
    @Mock
    lateinit var keycloak: Keycloak

    @Mock
    lateinit var realmResource: RealmResource

    @Mock
    lateinit var usersResource: UsersResource

    @Mock
    lateinit var userResource: UserResource

    private lateinit var provider: KeycloakUserProfileProvider

    @BeforeEach
    fun setUp() {
        provider =
            KeycloakUserProfileProvider(
                keycloak = keycloak,
                keycloakConfig =
                    AamKeycloakConfig(
                        serverUrl = "https://keycloak.test",
                        realm = "test-realm",
                        clientId = "backend",
                        clientSecret = "secret"
                    )
            )
        whenever(keycloak.realm(eq("test-realm"))).thenReturn(realmResource)
        whenever(realmResource.users()).thenReturn(usersResource)
        whenever(usersResource.get(eq("user-1"))).thenReturn(userResource)
    }

    private fun givenUser(representation: UserRepresentation) {
        whenever(userResource.toRepresentation()).thenReturn(representation)
    }

    private fun userWithEntity(entityId: String) =
        UserRepresentation().apply {
            email = "alice@example.org"
            attributes = mapOf("exact_username" to listOf(entityId))
        }

    @Test
    fun `should return the profile with the entity linked to the account`() {
        // given
        givenUser(userWithEntity("Child:alice"))

        // when
        val profile = provider.getUserProfile("user-1")

        // then
        assertThat(profile).isEqualTo(
            UserProfile(userIdentifier = "user-1", email = "alice@example.org", linkedEntityId = "Child:alice")
        )
    }

    @Test
    fun `should add the User prefix to a legacy entity id without entity type`() {
        // given
        givenUser(userWithEntity("alice"))

        // when
        val profile = provider.getUserProfile("user-1")

        // then
        assertThat(profile?.linkedEntityId).isEqualTo("User:alice")
    }

    @Test
    fun `should have no linked entity when none is linked to the account`() {
        // given
        givenUser(UserRepresentation())

        // when
        val profile = provider.getUserProfile("user-1")

        // then
        assertThat(profile).isNotNull
        assertThat(profile?.linkedEntityId).isNull()
    }

    @Test
    fun `should look up each user in Keycloak only once while cached`() {
        // given
        givenUser(userWithEntity("Child:alice"))

        // when
        provider.getUserProfile("user-1")
        val second = provider.getUserProfile("user-1")

        // then
        assertThat(second?.linkedEntityId).isEqualTo("Child:alice")
        verify(userResource, times(1)).toRepresentation()
    }

    @Test
    fun `should not cache a failed lookup`() {
        // given
        whenever(userResource.toRepresentation())
            .thenThrow(ProcessingException("connection refused"))
            .thenReturn(userWithEntity("Child:alice"))

        // when / then
        assertThatThrownBy { provider.getUserProfile("user-1") }.isInstanceOf(UserProfileLookupException::class.java)
        assertThat(provider.getUserProfile("user-1")?.linkedEntityId).isEqualTo("Child:alice")
    }

    @Test
    fun `should return no profile when the user does not exist in Keycloak`() {
        // given
        whenever(userResource.toRepresentation()).thenThrow(NotFoundException())

        // when
        val profile = provider.getUserProfile("user-1")

        // then
        assertThat(profile).isNull()
    }

    @Test
    fun `should fail when Keycloak cannot be reached, so the lookup can be retried`() {
        // given
        whenever(userResource.toRepresentation()).thenThrow(ProcessingException("connection refused"))

        // when / then
        assertThatThrownBy { provider.getUserProfile("user-1") }
            .isInstanceOf(UserProfileLookupException::class.java)
            .hasCauseInstanceOf(ProcessingException::class.java)
    }
}
