package com.aamdigital.aambackendservice.common.placeholder.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory

/** Thrown when a JSON tree uses a placeholder that no value was given for. */
class UnresolvedPlaceholderException(
    val placeholder: String
) : IllegalArgumentException("No value for placeholder $placeholder")

/**
 * Replaces [Placeholders] in any JSON tree (e.g. conditions or another configuration document)
 * with their values.
 *
 * Only string *values* that equal a known placeholder exactly are replaced, also inside arrays.
 * Object keys (and so query operators like `$eq`) and strings that merely contain a placeholder are
 * left as they are, as are strings that look like a placeholder but are not one of [knownPlaceholders].
 */
class PlaceholderResolver(
    private val objectMapper: ObjectMapper = ObjectMapper(),
    private val knownPlaceholders: Set<String> = Placeholders.ALL
) {
    /** The placeholders used anywhere in [node], so only their values need to be looked up. */
    fun findPlaceholders(node: JsonNode?): Set<String> =
        when {
            node == null -> emptySet()
            isPlaceholder(node) -> setOf(node.textValue())
            node.isContainerNode -> node.flatMap { findPlaceholders(it) }.toSet()
            else -> emptySet()
        }

    /**
     * A copy of [node] with each placeholder replaced by its value from [values].
     *
     * A placeholder without a value is an error rather than being replaced by `null`: in a query
     * `{"field": null}` also matches documents that do not have the field at all.
     *
     * @throws UnresolvedPlaceholderException if [node] uses a placeholder that [values] has no value for
     */
    fun resolve(
        node: JsonNode,
        values: Map<String, Any>
    ): JsonNode {
        val valueNodes = values.mapValues { (_, value) -> objectMapper.valueToTree<JsonNode>(value) }
        return replace(node, valueNodes)
    }

    private fun replace(
        node: JsonNode,
        values: Map<String, JsonNode>
    ): JsonNode =
        when {
            isPlaceholder(node) -> {
                values[node.textValue()]?.deepCopy() ?: throw UnresolvedPlaceholderException(node.textValue())
            }

            node.isObject -> {
                JsonNodeFactory.instance.objectNode().also { copy ->
                    node.properties().forEach { (key, value) -> copy.set<JsonNode>(key, replace(value, values)) }
                }
            }

            node.isArray -> {
                JsonNodeFactory.instance.arrayNode().also { copy ->
                    node.forEach { element -> copy.add(replace(element, values)) }
                }
            }

            else -> {
                node
            }
        }

    private fun isPlaceholder(node: JsonNode): Boolean = node.isTextual && node.textValue() in knownPlaceholders
}
