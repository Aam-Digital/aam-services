package com.aamdigital.aambackendservice.common.condition

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DocumentConditionEngineTest {
    private val service = DocumentConditionEngine()
    private val objectMapper = ObjectMapper()

    @Test
    fun `should parse nested logical condition tree into conjunction groups`() {
        val conditionTree =
            objectMapper.readTree(
                """
                {
                  "${'$'}or": [
                    {"name": {"${'$'}eq": "Bert"}},
                    {
                      "${'$'}and": [
                        {"age": {"${'$'}gte": "18"}},
                        {"categories": {"${'$'}elemMatch": "X"}}
                      ]
                    }
                  ]
                }
                """.trimIndent()
            )

        val groups = service.parseConditionGroups(conditionTree)

        assertThat(groups).hasSize(2)
        assertThat(groups.map { it.map { condition -> condition.field } })
            .containsExactlyInAnyOrder(listOf("name"), listOf("age", "categories"))
    }

    @Test
    fun `should evaluate conditions against document map`() {
        val document =
            mapOf<String, Any>(
                "name" to "Bert",
                "age" to 18,
                "categories" to listOf("X", "Y")
            )

        val matchingConditions =
            listOf(
                DocumentCondition(field = "name", operator = "${'$'}eq", value = "Bert"),
                DocumentCondition(field = "categories", operator = "${'$'}elemMatch", value = "X")
            )
        val nonMatchingConditions =
            listOf(
                DocumentCondition(field = "age", operator = "${'$'}gt", value = "18")
            )

        assertThat(service.matchesAll(matchingConditions, document)).isTrue()
        assertThat(service.matchesAll(nonMatchingConditions, document)).isFalse()
    }
}
