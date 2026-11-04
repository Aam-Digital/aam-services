package com.aamdigital.aambackendservice.common.placeholder.core

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PlaceholderResolverTest {
    private val objectMapper = jacksonObjectMapper()
    private val resolver = PlaceholderResolver(objectMapper)

    private fun json(value: String) = objectMapper.readTree(value)

    @Test
    fun `should replace placeholder values in nested objects and arrays`() {
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
        val resolved = resolver.resolve(node, mapOf(Placeholders.CURRENT_USER to "User:alice"))

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
        val resolved = resolver.resolve(node, mapOf(Placeholders.CURRENT_USER to "User:alice"))

        // then
        assertThat(resolved).isEqualTo(node)
    }

    @Test
    fun `should not change the given tree`() {
        // given
        val node = json("""{"assignedTo": "${'$'}current_user"}""")

        // when
        resolver.resolve(node, mapOf(Placeholders.CURRENT_USER to "User:alice"))

        // then
        assertThat(node).isEqualTo(json("""{"assignedTo": "${'$'}current_user"}"""))
    }

    @Test
    fun `should fail instead of using null for a placeholder without a value`() {
        // given
        val node = json("""{"assignedTo": "${'$'}current_user"}""")

        // when / then
        assertThatThrownBy { resolver.resolve(node, emptyMap()) }
            .isInstanceOfSatisfying(UnresolvedPlaceholderException::class.java) { ex ->
                assertThat(ex.placeholder).isEqualTo(Placeholders.CURRENT_USER)
            }
    }

    @Test
    fun `should not need values for a tree without placeholders`() {
        // given
        val node = json("""{"name": {"${'$'}eq": "Bert"}}""")

        // when
        val resolved = resolver.resolve(node, emptyMap())

        // then
        assertThat(resolved).isEqualTo(node)
    }

    @Test
    fun `should find the placeholders used in a tree`() {
        assertThat(resolver.findPlaceholders(json("""{"a": [{"b": "${'$'}current_user"}], "c": "x"}""")))
            .containsExactly(Placeholders.CURRENT_USER)
        assertThat(resolver.findPlaceholders(json("""{"${'$'}current_user": "x"}"""))).isEmpty()
        assertThat(resolver.findPlaceholders(null)).isEmpty()
    }
}
