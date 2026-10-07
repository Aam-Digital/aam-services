package com.aamdigital.aambackendservice.common.condition

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.NullNode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class DocumentConditionEngineTest {
    private val service = DocumentConditionEngine()
    private val objectMapper = ObjectMapper()

    private val document =
        mapOf(
            "name" to "Bert",
            "age" to 18,
            "gender" to "M",
            "categories" to listOf("X", "Y"),
            "center" to mapOf("id" to "center-1"),
            "dateOfBirth" to "2008-05-01"
        )

    private fun matches(conditions: String): Boolean =
        service.matches(service.parse(objectMapper.readTree(conditions.replace("€", "$"))), document)

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("conditionCases")
    fun `should evaluate conditions with MongoDB semantics`(
        conditions: String,
        expected: Boolean
    ) {
        assertThat(matches(conditions)).isEqualTo(expected)
    }

    @Test
    fun `should match every document for missing or null conditions`() {
        assertThat(service.matches(service.parse(null), document)).isTrue()
        assertThat(service.matches(service.parse(NullNode.instance), document)).isTrue()
    }

    @Test
    fun `should reject conditions that are not an object`() {
        assertThatThrownBy { service.parse(objectMapper.readTree("[]")) }
            .isInstanceOf(InvalidDocumentConditionsException::class.java)
        assertThatThrownBy { service.parse(objectMapper.readTree("\"Bert\"")) }
            .isInstanceOf(InvalidDocumentConditionsException::class.java)
    }

    @Test
    fun `should reject unknown operators when parsing`() {
        assertThatThrownBy { service.parse(objectMapper.readTree("""{"name": {"${'$'}foo": "Bert"}}""")) }
            .isInstanceOf(InvalidDocumentConditionsException::class.java)
        assertThatThrownBy { service.parse(objectMapper.readTree("""{"${'$'}not": {"name": "Bert"}}""")) }
            .isInstanceOf(InvalidDocumentConditionsException::class.java)
    }

    @Test
    fun `should reject an unknown operator that is only reached for some documents when matching`() {
        // given the operator is behind a branch that is false for an empty document
        val conditions =
            service.parse(
                objectMapper.readTree("""{"${'$'}and": [{"name": "Bert"}, {"age": {"${'$'}foo": 1}}]}""")
            )

        // when / then
        assertThatThrownBy { service.matches(conditions, document) }
            .isInstanceOf(InvalidDocumentConditionsException::class.java)
    }

    @Test
    fun `should give the same canonical form regardless of key order`() {
        val first = service.parse(objectMapper.readTree("""{"name": "Bert", "age": {"${'$'}gte": 18}}"""))
        val second = service.parse(objectMapper.readTree("""{"age": {"${'$'}gte": 18}, "name": "Bert"}"""))
        val different = service.parse(objectMapper.readTree("""{"name": "Bert", "age": {"${'$'}gte": 19}}"""))

        assertThat(first.canonicalJson).isEqualTo(second.canonicalJson)
        assertThat(first).isEqualTo(second)
        assertThat(first).isNotEqualTo(different)
    }

    companion object {
        // `€` stands in for `$`, which would otherwise have to be escaped in every condition
        private fun case(
            conditions: String,
            expected: Boolean
        ) = Arguments.of(conditions, expected)

        @JvmStatic
        fun conditionCases(): List<Arguments> =
            listOf(
                // conditions as produced by the frontend's conditions editor
                case("""{"€or": [{"name": "Bert"}]}""", true),
                case("""{"€or": [{"name": "Clark"}]}""", false),
                case("""{"€or": [{"name": "Clark"}, {"age": 18}]}""", true),
                case("""{"€or": [{"gender": {"€in": ["M", "F"]}}]}""", true),
                case("""{"€or": [{"gender": {"€in": ["F"]}}]}""", false),
                case("""{"€or": [{"categories": {"€elemMatch": {"€in": ["Y", "Z"]}}}]}""", true),
                case("""{"€or": [{"categories": {"€elemMatch": {"€in": ["Z"]}}}]}""", false),
                // ... and their negation
                case("""{"€or": [{"name": {"€not": {"€eq": "Bert"}}}]}""", false),
                case("""{"€or": [{"name": {"€not": {"€eq": "Clark"}}}]}""", true),
                case("""{"€or": [{"gender": {"€not": {"€in": ["F"]}}}]}""", true),
                case("""{"€or": [{"gender": {"€not": {"€in": ["M"]}}}]}""", false),
                case("""{"€or": [{"categories": {"€not": {"€elemMatch": {"€in": ["Z"]}}}}]}""", true),
                case("""{"€or": [{"categories": {"€not": {"€elemMatch": {"€in": ["X"]}}}}]}""", false),
                case("""{"€or": [{"missing": {"€not": {"€eq": "x"}}}]}""", true),
                // legacy formats
                case("""{"name": "Bert", "age": 18}""", true),
                case("""{"name": "Bert", "age": 17}""", false),
                case("""{"categories": {"€elemMatch": "X"}}""", true),
                case("""{"categories": {"€elemMatch": "Z"}}""", false),
                case("""{"categories": {"€elemMatch": ["Z", "X"]}}""", true),
                case("""{"categories": {"€not": {"€elemMatch": "X"}}}""", false),
                // logical operators
                case("""{"€and": [{"name": "Bert"}, {"€or": [{"age": 17}, {"age": 18}]}]}""", true),
                case("""{"€nor": [{"name": "Bert"}]}""", false),
                case("""{"€nor": [{"name": "Clark"}]}""", true),
                // comparison operators
                case("""{"name": {"€ne": "Bert"}}""", false),
                case("""{"gender": {"€nin": ["F"]}}""", true),
                case("""{"age": {"€gt": 18}}""", false),
                case("""{"age": {"€gte": 18}}""", true),
                case("""{"age": {"€lt": 18.5}}""", true),
                case("""{"age": {"€lte": 17}}""", false),
                case("""{"dateOfBirth": {"€gte": "2008-01-01"}}""", true),
                case("""{"dateOfBirth": {"€lt": "2008-01-01"}}""", false),
                // MongoDB semantics: a number written as a string does not compare with a number
                case("""{"age": {"€gte": "18"}}""", false),
                // arrays match if any element does
                case("""{"categories": "Y"}""", true),
                case("""{"categories": {"€all": ["X", "Y"]}}""", true),
                // other operators and dotted paths
                case("""{"center.id": "center-1"}""", true),
                case("""{"center.id": "center-2"}""", false),
                case("""{"missing": {"€exists": false}}""", true),
                case("""{"name": {"€exists": false}}""", false),
                case("""{"name": {"€regex": "^be", "€options": "i"}}""", true),
                case("""{}""", true)
            )
    }
}
