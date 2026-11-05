package com.aamdigital.aambackendservice.common.placeholder.core

import com.aamdigital.aambackendservice.common.keycloak.core.UserProfile
import com.aamdigital.aambackendservice.common.keycloak.core.UserProfileLookupException
import com.aamdigital.aambackendservice.common.keycloak.core.UserProfileProvider
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PlaceholderResolverTest {
    /** User profiles as Keycloak would have them, recording which lookups were made. */
    private class FakeUserProfileProvider(
        private val linkedEntityId: String? = "User:alice",
        private val failure: RuntimeException? = null
    ) : UserProfileProvider {
        val lookups = mutableListOf<String>()

        override fun getUserProfile(userIdentifier: String): UserProfile {
            lookups.add(userIdentifier)
            failure?.let { throw it }
            return UserProfile(userIdentifier, email = null, linkedEntityId = linkedEntityId)
        }
    }

    private val objectMapper = jacksonObjectMapper()
    private val userProfiles = FakeUserProfileProvider()
    private val resolver = PlaceholderResolver(userProfiles, objectMapper)

    private fun json(value: String) = objectMapper.readTree(value)

    @Test
    fun `should replace the current user placeholder in nested objects and arrays`() {
        // given
        val node =
            json(
                """
                {
                    "${'$'}or": [
                        {"assignedTo": {"${'$'}elemMatch": {"${'$'}eq": "${'$'}current_user"}}},
                        {"authors": {"${'$'}in": ["${'$'}current_user", "User:bob"]}}
                    ]
                }
                """.trimIndent()
            )

        // when
        val resolved = resolver.resolve(node, "user-1")

        // then
        assertThat(resolved).isEqualTo(
            json(
                """
                {
                    "${'$'}or": [
                        {"assignedTo": {"${'$'}elemMatch": {"${'$'}eq": "User:alice"}}},
                        {"authors": {"${'$'}in": ["User:alice", "User:bob"]}}
                    ]
                }
                """.trimIndent()
            )
        )
        assertThat(userProfiles.lookups).containsExactly("user-1")
    }

    @Test
    fun `should leave keys, strings containing a placeholder and unknown placeholders unchanged`() {
        // given
        val node =
            json(
                """
                {
                    "${'$'}current_user": "x",
                    "name": "my ${'$'}current_user",
                    "other": "${'$'}unknown_placeholder",
                    "age": 18
                }
                """.trimIndent()
            )

        // when
        val resolved = resolver.resolve(node, "user-1")

        // then
        assertThat(resolved).isEqualTo(node)
    }

    @Test
    fun `should not change the given tree`() {
        // given
        val node = json("""{"assignedTo": "${'$'}current_user"}""")

        // when
        resolver.resolve(node, "user-1")

        // then
        assertThat(node).isEqualTo(json("""{"assignedTo": "${'$'}current_user"}"""))
    }

    @Test
    fun `should not look up the user for a tree without placeholders`() {
        // given
        val node = json("""{"name": {"${'$'}eq": "Bert"}}""")

        // when
        val resolved = resolver.resolve(node, "user-1")

        // then
        assertThat(resolved).isEqualTo(node)
        assertThat(userProfiles.lookups).isEmpty()
    }

    @Test
    fun `should fail instead of using null when no entity is linked to the account`() {
        // given
        val resolverWithoutEntity = PlaceholderResolver(FakeUserProfileProvider(linkedEntityId = null))

        // when / then
        assertThatThrownBy { resolverWithoutEntity.resolve(json("""{"a": "${'$'}current_user"}"""), "user-1") }
            .isInstanceOfSatisfying(UnresolvedPlaceholderException::class.java) { ex ->
                assertThat(ex.placeholder).isEqualTo(Placeholders.CURRENT_USER)
            }
    }

    @Test
    fun `should have no values without a user profile provider`() {
        assertThatThrownBy { PlaceholderResolver(null).resolve(json("""{"a": "${'$'}current_user"}"""), "user-1") }
            .isInstanceOf(UnresolvedPlaceholderException::class.java)
    }

    @Test
    fun `should fail when the profile cannot be looked up for now, so the lookup can be retried`() {
        // given
        val failingResolver =
            PlaceholderResolver(FakeUserProfileProvider(failure = UserProfileLookupException("keycloak unreachable")))

        // when / then
        assertThatThrownBy { failingResolver.resolve(json("""{"a": "${'$'}current_user"}"""), "user-1") }
            .isInstanceOf(PlaceholderValueLookupException::class.java)
            .hasCauseInstanceOf(UserProfileLookupException::class.java)
    }
}
