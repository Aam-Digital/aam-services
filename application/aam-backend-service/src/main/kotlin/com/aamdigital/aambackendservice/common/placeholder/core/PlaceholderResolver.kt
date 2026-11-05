package com.aamdigital.aambackendservice.common.placeholder.core

import com.aamdigital.aambackendservice.common.keycloak.core.UserProfileLookupException
import com.aamdigital.aambackendservice.common.keycloak.core.UserProfileProvider
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/** Thrown when a JSON tree uses a placeholder that has no value for the user. */
class UnresolvedPlaceholderException(
    val placeholder: String
) : IllegalArgumentException("No value for placeholder $placeholder")

/**
 * Thrown when placeholder values cannot be looked up for now (e.g. Keycloak is unreachable), so
 * the caller can try again later.
 */
class PlaceholderValueLookupException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Replaces [Placeholders] in any JSON tree (e.g. conditions or another configuration document)
 * with their values for a user:
 *
 * - [Placeholders.CURRENT_USER] is the entity linked to the user account, from [userProfileProvider].
 *
 * Only string *values* that equal a known placeholder exactly are replaced, also inside arrays.
 * Object keys (and so query operators like `$eq`) and strings that merely contain a placeholder are
 * left as they are. Values are only looked up when the tree actually uses a placeholder.
 *
 * Without a [userProfileProvider] (Keycloak admin access is not configured) user placeholders have
 * no value. That is logged once, on the first tree that actually uses one, so an instance that uses
 * none is not told about it.
 */
class PlaceholderResolver(
    private val userProfileProvider: UserProfileProvider?,
    private val objectMapper: ObjectMapper = ObjectMapper()
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val warnedUnavailable = AtomicBoolean(false)

    /**
     * A copy of [node] with each placeholder replaced by its value for the user [userIdentifier],
     * or [node] itself if it uses none.
     *
     * A placeholder without a value is an error rather than being replaced by `null`: in a query
     * `{"field": null}` also matches documents that do not have the field at all.
     *
     * @throws UnresolvedPlaceholderException if [node] uses a placeholder that has no value for the
     *     user (e.g. no entity is linked to the account)
     * @throws PlaceholderValueLookupException if the values cannot be looked up for now
     */
    fun resolve(
        node: JsonNode,
        userIdentifier: String
    ): JsonNode {
        val usedPlaceholders = findPlaceholders(node)
        if (usedPlaceholders.isEmpty()) {
            return node
        }

        val values =
            lookupValues(userIdentifier, usedPlaceholders)
                .mapValues { (_, value) -> objectMapper.valueToTree<JsonNode>(value) }
        return replace(node, values)
    }

    private fun lookupValues(
        userIdentifier: String,
        placeholders: Set<String>
    ): Map<String, Any> {
        if (userProfileProvider == null) {
            if (warnedUnavailable.compareAndSet(false, true)) {
                logger.warn(
                    "Placeholders {} are used, but cannot be resolved because Keycloak admin access " +
                        "(keycloak.server-url) is not configured. Configuration using them is ignored.",
                    placeholders
                )
            }
            return emptyMap()
        }

        val profile =
            try {
                userProfileProvider.getUserProfile(userIdentifier)
            } catch (ex: UserProfileLookupException) {
                throw PlaceholderValueLookupException("Could not look up placeholder values of $userIdentifier", ex)
            }

        return listOfNotNull(
            profile?.linkedEntityId?.let { Placeholders.CURRENT_USER to it }
        ).toMap()
    }

    private fun findPlaceholders(node: JsonNode): Set<String> =
        when {
            isPlaceholder(node) -> setOf(node.textValue())
            node.isContainerNode -> node.flatMap { findPlaceholders(it) }.toSet()
            else -> emptySet()
        }

    private fun replace(
        node: JsonNode,
        values: Map<String, JsonNode>
    ): JsonNode =
        when {
            isPlaceholder(node) -> {
                values[node.textValue()]?.deepCopy<JsonNode>() ?: throw UnresolvedPlaceholderException(node.textValue())
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

    private fun isPlaceholder(node: JsonNode): Boolean = node.isTextual && node.textValue() in Placeholders.ALL
}
