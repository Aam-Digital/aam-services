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
            listOf(DocumentCondition(field = "age", operator = "${'$'}gt", value = "18"))

        assertThat(service.matchesAll(matchingConditions, document)).isTrue()
        assertThat(service.matchesAll(nonMatchingConditions, document)).isFalse()
    }

    @Test
    fun `should evaluate numeric operators with boundary values`() {
        val youngerDocument = mapOf<String, Any>("age" to 17)
        val exactDocument = mapOf<String, Any>("age" to 18)
        val olderDocument = mapOf<String, Any>("age" to 19)

        val gtCondition = DocumentCondition(field = "age", operator = "${'$'}gt", value = "18")
        val gteCondition = DocumentCondition(field = "age", operator = "${'$'}gte", value = "18")
        val ltCondition = DocumentCondition(field = "age", operator = "${'$'}lt", value = "18")
        val lteCondition = DocumentCondition(field = "age", operator = "${'$'}lte", value = "18")

        assertThat(service.matches(gtCondition, youngerDocument)).isFalse()
        assertThat(service.matches(gtCondition, exactDocument)).isFalse()
        assertThat(service.matches(gtCondition, olderDocument)).isTrue()

        assertThat(service.matches(gteCondition, youngerDocument)).isFalse()
        assertThat(service.matches(gteCondition, exactDocument)).isTrue()
        assertThat(service.matches(gteCondition, olderDocument)).isTrue()

        assertThat(service.matches(ltCondition, youngerDocument)).isTrue()
        assertThat(service.matches(ltCondition, exactDocument)).isFalse()
        assertThat(service.matches(ltCondition, olderDocument)).isFalse()

        assertThat(service.matches(lteCondition, youngerDocument)).isTrue()
        assertThat(service.matches(lteCondition, exactDocument)).isTrue()
        assertThat(service.matches(lteCondition, olderDocument)).isFalse()
    }

    @Test
    fun `should support decimal and negative numeric condition values`() {
        val document = mapOf<String, Any>("score" to -4.5f)

        assertThat(service.matches(DocumentCondition("score", "${'$'}gt", "-5.0"), document)).isTrue()
        assertThat(service.matches(DocumentCondition("score", "${'$'}lt", "-4.0"), document)).isTrue()
        assertThat(service.matches(DocumentCondition("score", "${'$'}gte", "-4.5"), document)).isTrue()
        assertThat(service.matches(DocumentCondition("score", "${'$'}lte", "-4.5"), document)).isTrue()
    }

    @Test
    fun `should return false for unknown operator and type mismatch`() {
        val document = mapOf<String, Any>("age" to "18")

        assertThat(service.matches(DocumentCondition("age", "${'$'}unknown", "18"), document)).isFalse()
        assertThat(service.matches(DocumentCondition("age", "${'$'}gt", "17"), document)).isFalse()
    }

    @Test
    fun `should parse null and invalid condition nodes safely`() {
        val groupsFromNull = service.parseConditionGroups(null)
        val groupsFromInvalidNode = service.parseConditionGroups(objectMapper.readTree("[]"))

        assertThat(groupsFromNull).containsExactly(emptyList())
        assertThat(groupsFromInvalidNode).containsExactly(emptyList())
    }

    @Test
    fun `should match list values for eq only with single element lists`() {
        val singleElementListDocument = mapOf<String, Any>("tags" to listOf("X"))
        val multiElementListDocument = mapOf<String, Any>("tags" to listOf("X", "Y"))

        val eqCondition = DocumentCondition("tags", "${'$'}eq", "X")

        assertThat(service.matches(eqCondition, singleElementListDocument)).isTrue()
        assertThat(service.matches(eqCondition, multiElementListDocument)).isFalse()
    }
}
