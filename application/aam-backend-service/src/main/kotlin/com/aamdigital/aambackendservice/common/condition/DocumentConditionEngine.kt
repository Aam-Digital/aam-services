package com.aamdigital.aambackendservice.common.condition

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory

/** Represents one atomic field comparison used in a document condition tree. */
data class DocumentCondition(
    val field: String,
    val operator: String,
    val value: String
)

/** Parses JSON condition trees and evaluates atomic conditions against generic document maps. */
class DocumentConditionEngine {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Expands nested logical conditions to disjunctive normal form so each returned list
     * represents one conjunction.
     */
    fun parseConditionGroups(conditionNode: JsonNode?): List<List<DocumentCondition>> {
        if (conditionNode == null || conditionNode.isNull || conditionNode.isMissingNode) {
            return listOf(emptyList())
        }

        if (!conditionNode.isObject) {
            logger.warn("Skipping invalid condition node of type {}", conditionNode.nodeType)
            return listOf(emptyList())
        }

        var groups: List<List<DocumentCondition>> = listOf(emptyList())
        for ((key, value) in conditionNode.properties()) {
            val keyGroups =
                when (key) {
                    "\$or" -> parseOrGroups(value)
                    "\$and" -> parseAndGroups(value)
                    else -> parseFieldConditionGroups(field = key, condition = value)
                }

            groups = andCombine(groups, keyGroups)
        }

        return groups
    }

    fun matchesAll(
        conditions: List<DocumentCondition>,
        document: Map<*, *>
    ): Boolean = conditions.all { matches(it, document) }

    fun matches(
        condition: DocumentCondition,
        document: Map<*, *>
    ): Boolean {
        val currentValue = document[condition.field]
        val conditionValue = condition.value

        return when (condition.operator) {
            "\$eq" -> {
                when (currentValue) {
                    is String -> currentValue == conditionValue
                    is List<*> ->
                        currentValue.size == 1 && currentValue.first() == conditionValue

                    else -> false
                }
            }

            "\$nq" -> {
                when (currentValue) {
                    is String -> currentValue != conditionValue
                    is List<*> ->
                        currentValue.size == 1 && currentValue.first() != conditionValue

                    else -> false
                }
            }

            "\$elemMatch" -> {
                when (currentValue) {
                    is String -> currentValue == conditionValue
                    is List<*> -> currentValue.contains(conditionValue)
                    else -> false
                }
            }

            "\$gt" -> {
                when (currentValue) {
                    is Number -> conditionValue.toFloatOrNull()?.let { currentValue.toFloat() > it } ?: false

                    else -> false
                }
            }

            "\$gte" -> {
                when (currentValue) {
                    is Number -> conditionValue.toFloatOrNull()?.let { currentValue.toFloat() >= it } ?: false

                    else -> false
                }
            }

            "\$lt" -> {
                when (currentValue) {
                    is Number -> conditionValue.toFloatOrNull()?.let { currentValue.toFloat() < it } ?: false

                    else -> false
                }
            }

            "\$lte" -> {
                when (currentValue) {
                    is Number -> conditionValue.toFloatOrNull()?.let { currentValue.toFloat() <= it } ?: false

                    else -> false
                }
            }

            else -> {
                logger.warn("Unknown condition operator: {}", condition.operator)
                false
            }
        }
    }

    private fun parseOrGroups(orNode: JsonNode): List<List<DocumentCondition>> {
        if (!orNode.isArray) {
            logger.warn("Skipping invalid \$or condition because value is not an array")
            return listOf(emptyList())
        }

        return orNode
            .flatMap { parseConditionGroups(it) }
            .ifEmpty { listOf(emptyList()) }
    }

    private fun parseAndGroups(andNode: JsonNode): List<List<DocumentCondition>> {
        if (!andNode.isArray) {
            logger.warn("Skipping invalid \$and condition because value is not an array")
            return listOf(emptyList())
        }

        var groups: List<List<DocumentCondition>> = listOf(emptyList())
        andNode.forEach { node ->
            groups = andCombine(groups, parseConditionGroups(node))
        }

        return groups
    }

    private fun parseFieldConditionGroups(
        field: String,
        condition: JsonNode
    ): List<List<DocumentCondition>> {
        if (!condition.isObject) {
            val equalsCondition =
                DocumentCondition(
                    field = field,
                    operator = "\$eq",
                    value = condition.asText()
                )
            return listOf(listOf(equalsCondition))
        }

        val conditions =
            condition
                .properties()
                .asSequence()
                .map { (operator, valueNode) ->
                    DocumentCondition(
                        field = field,
                        operator = operator,
                        value =
                            when {
                                valueNode.isValueNode -> valueNode.asText()
                                else -> valueNode.toString()
                            }
                    )
                }.toList()

        return listOf(conditions)
    }

    private fun andCombine(
        left: List<List<DocumentCondition>>,
        right: List<List<DocumentCondition>>
    ): List<List<DocumentCondition>> =
        left.flatMap { leftConditions ->
            right.map { rightConditions ->
                leftConditions + rightConditions
            }
        }
}
