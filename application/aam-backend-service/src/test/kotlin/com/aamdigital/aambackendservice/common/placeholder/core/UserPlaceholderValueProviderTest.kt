package com.aamdigital.aambackendservice.common.placeholder.core

import com.aamdigital.aambackendservice.common.keycloak.core.UserProfile
import com.aamdigital.aambackendservice.common.keycloak.core.UserProfileLookupException
import com.aamdigital.aambackendservice.common.keycloak.core.UserProfileProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class UserPlaceholderValueProviderTest {
    /** User profiles as Keycloak would have them, recording which lookups were made. */
    private class FakeUserProfileProvider(
        private val profile: UserProfile? = null,
        private val failure: RuntimeException? = null
    ) : UserProfileProvider {
        val lookups = mutableListOf<String>()

        override fun getUserProfile(userIdentifier: String): UserProfile? {
            lookups.add(userIdentifier)
            failure?.let { throw it }
            return profile
        }
    }

    private val placeholders = setOf(Placeholders.CURRENT_USER)

    @Test
    fun `should resolve current user to the entity linked to the account`() {
        // given
        val profiles = FakeUserProfileProvider(UserProfile("user-1", null, linkedEntityId = "User:alice"))

        // when
        val values = UserPlaceholderValueProvider(profiles).getValues("user-1", placeholders)

        // then
        assertThat(values).isEqualTo(mapOf(Placeholders.CURRENT_USER to "User:alice"))
    }

    @Test
    fun `should have no value when no entity is linked to the account or the account does not exist`() {
        val withoutEntity = FakeUserProfileProvider(UserProfile("user-1", null, linkedEntityId = null))
        val withoutAccount = FakeUserProfileProvider(profile = null)

        assertThat(UserPlaceholderValueProvider(withoutEntity).getValues("user-1", placeholders)).isEmpty()
        assertThat(UserPlaceholderValueProvider(withoutAccount).getValues("user-1", placeholders)).isEmpty()
    }

    @Test
    fun `should have no values without a user profile provider`() {
        assertThat(UserPlaceholderValueProvider(null).getValues("user-1", placeholders)).isEmpty()
    }

    @Test
    fun `should fail when the profile cannot be looked up for now, so the lookup can be retried`() {
        // given
        val profiles = FakeUserProfileProvider(failure = UserProfileLookupException("keycloak unreachable"))

        // when / then
        assertThatThrownBy { UserPlaceholderValueProvider(profiles).getValues("user-1", placeholders) }
            .isInstanceOf(PlaceholderValueLookupException::class.java)
            .hasCauseInstanceOf(UserProfileLookupException::class.java)
    }

    @Test
    fun `should not look up the profile when no user placeholder is needed`() {
        // given
        val profiles = FakeUserProfileProvider(UserProfile("user-1", null, linkedEntityId = "User:alice"))

        // when
        val values = UserPlaceholderValueProvider(profiles).getValues("user-1", emptySet())

        // then
        assertThat(values).isEmpty()
        assertThat(profiles.lookups).isEmpty()
    }
}
