package com.aamdigital.aambackendservice.common.placeholder.core

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
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class KeycloakPlaceholderValueProviderTest {
    @Mock
    lateinit var keycloak: Keycloak

    @Mock
    lateinit var realmResource: RealmResource

    @Mock
    lateinit var usersResource: UsersResource

    @Mock
    lateinit var userResource: UserResource

    private lateinit var provider: KeycloakPlaceholderValueProvider

    private val placeholders = setOf(Placeholders.CURRENT_USER)

    @BeforeEach
    fun setUp() {
        provider =
            KeycloakPlaceholderValueProvider(
                keycloak = keycloak,
                keycloakConfig =
                    AamKeycloakConfig(
                        serverUrl = "https://keycloak.test",
                        realm = "test-realm",
                        clientId = "backend",
                        clientSecret = "secret"
                    )
            )
    }

    private fun givenUserResource() {
        whenever(keycloak.realm(eq("test-realm"))).thenReturn(realmResource)
        whenever(realmResource.users()).thenReturn(usersResource)
        whenever(usersResource.get(eq("user-1"))).thenReturn(userResource)
    }

    private fun givenUser(representation: UserRepresentation) {
        givenUserResource()
        whenever(userResource.toRepresentation()).thenReturn(representation)
    }

    private fun userWithEntity(entityId: String) =
        UserRepresentation().apply { attributes = mapOf("exact_username" to listOf(entityId)) }

    @Test
    fun `should resolve current user to the entity linked to the account`() {
        // given
        givenUser(userWithEntity("Child:alice"))

        // when
        val values = provider.getValues("user-1", placeholders)

        // then
        assertThat(values).isEqualTo(mapOf(Placeholders.CURRENT_USER to "Child:alice"))
    }

    @Test
    fun `should add the User prefix to a legacy entity id without entity type`() {
        // given
        givenUser(userWithEntity("alice"))

        // when
        val values = provider.getValues("user-1", placeholders)

        // then
        assertThat(values).isEqualTo(mapOf(Placeholders.CURRENT_USER to "User:alice"))
    }

    @Test
    fun `should have no value when no entity is linked to the account`() {
        // given
        givenUser(UserRepresentation())

        // when
        val values = provider.getValues("user-1", placeholders)

        // then
        assertThat(values).isEmpty()
    }

    @Test
    fun `should have no value when the user does not exist in Keycloak`() {
        // given
        givenUserResource()
        whenever(userResource.toRepresentation()).thenThrow(NotFoundException())

        // when
        val values = provider.getValues("user-1", placeholders)

        // then
        assertThat(values).isEmpty()
    }

    @Test
    fun `should fail when Keycloak cannot be reached, so the lookup can be retried`() {
        // given
        givenUserResource()
        whenever(userResource.toRepresentation()).thenThrow(ProcessingException("connection refused"))

        // when / then
        assertThatThrownBy { provider.getValues("user-1", placeholders) }
            .isInstanceOf(PlaceholderValueLookupException::class.java)
            .hasCauseInstanceOf(ProcessingException::class.java)
    }

    @Test
    fun `should not call Keycloak when no user placeholder is needed`() {
        // when
        val values = provider.getValues("user-1", emptySet())

        // then
        assertThat(values).isEmpty()
        verifyNoInteractions(keycloak)
    }
}
