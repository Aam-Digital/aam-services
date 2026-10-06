package com.aamdigital.aambackendservice.common.condition

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import de.bwaldvogel.mongo.backend.DefaultQueryMatcher
import de.bwaldvogel.mongo.bson.Document

/**
 * A condition tree in MongoDB query syntax (e.g. `{"$or": [{"name": "Bert"}, {"age": {"$gte": 18}}]}`),
 * parsed by [DocumentConditionEngine.parse] and ready to be matched against documents.
 *
 * [canonicalJson] is the conditions serialized with sorted keys, so equal conditions always give
 * the same string, e.g. to derive a stable identifier from them.
 */
class DocumentConditions internal constructor(
    internal val query: Document,
    val canonicalJson: String
) {
    override fun equals(other: Any?): Boolean = other is DocumentConditions && other.canonicalJson == canonicalJson

    override fun hashCode(): Int = canonicalJson.hashCode()

    override fun toString(): String = canonicalJson
}

/** Thrown when a condition tree is not a valid MongoDB query. */
class InvalidDocumentConditionsException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

/**
 * Evaluates conditions in MongoDB query syntax against generic documents, with MongoDB's semantics.
 *
 * This is the syntax the frontend stores conditions in, and evaluates them with (through
 * `@ucast/mongo2js`). Rather than implementing those operators again, matching is delegated to
 * the query matcher of mongo-java-server, which is tested against a real MongoDB. It supports all
 * query operators the frontend uses, among them `$and`, `$or`, `$nor`, `$not`, `$eq`, `$ne`,
 * `$in`, `$nin`, `$gt(e)`, `$lt(e)`, `$exists`, `$regex`, `$elemMatch` and `$all`, as well as
 * dotted paths into nested objects.
 */
class DocumentConditionEngine {
    private val canonicalMapper =
        ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)

    /**
     * Parse a condition tree. A missing, null or empty one matches every document.
     *
     * @throws InvalidDocumentConditionsException if the conditions are not a valid query,
     * e.g. because they are not an object or use an unknown operator.
     */
    fun parse(conditionNode: JsonNode?): DocumentConditions {
        if (conditionNode == null || conditionNode.isNull || conditionNode.isMissingNode) {
            return parse(canonicalMapper.createObjectNode())
        }
        if (!conditionNode.isObject) {
            throw InvalidDocumentConditionsException(
                "Conditions must be an object, but are of type ${conditionNode.nodeType}"
            )
        }

        val plainConditions = canonicalMapper.treeToValue(conditionNode, Map::class.java)
        val conditions =
            DocumentConditions(
                query = normalizeLegacyElemMatch(toBson(plainConditions)) as Document,
                canonicalJson = canonicalMapper.writeValueAsString(plainConditions)
            )

        // Malformed queries are rejected by the matcher regardless of the document, so most are
        // already found here, against an empty document, instead of on every change. Operators
        // the matcher never reaches for an empty document (e.g. after a false branch of an
        // `$and`) are only found by [matches].
        evaluate(conditions, Document())

        return conditions
    }

    /**
     * Whether the [document] matches the [conditions].
     *
     * @throws InvalidDocumentConditionsException if the conditions turn out not to be a valid query.
     */
    fun matches(
        conditions: DocumentConditions,
        document: Map<*, *>
    ): Boolean = evaluate(conditions, toBson(document) as Document)

    private fun evaluate(
        conditions: DocumentConditions,
        document: Document
    ): Boolean =
        try {
            // a new matcher for each evaluation, as it keeps state between calls and is not thread-safe
            DefaultQueryMatcher().matches(document, conditions.query)
        } catch (ex: RuntimeException) {
            throw InvalidDocumentConditionsException("Invalid conditions $conditions: ${ex.message}", ex)
        }

    /**
     * Older frontend versions stored array conditions as `{"$elemMatch": "value"}` (or a list of
     * values), which MongoDB rejects. The frontend still reads them as "contains the value", so
     * they are rewritten to the equivalent valid form here.
     */
    private fun normalizeLegacyElemMatch(value: Any?): Any? =
        when (value) {
            is Document -> {
                Document().also { doc ->
                    value.forEach { (key, v) ->
                        doc[key] =
                            when {
                                key != "\$elemMatch" || v is Document -> normalizeLegacyElemMatch(v)
                                v is List<*> -> Document("\$in", v)
                                else -> Document("\$eq", v)
                            }
                    }
                }
            }

            is List<*> -> {
                value.map { normalizeLegacyElemMatch(it) }
            }

            else -> {
                value
            }
        }

    /** Deep-convert to the matcher's types: nested objects must be [Document]s for dotted paths to match into them. */
    private fun toBson(value: Any?): Any? =
        when (value) {
            is Map<*, *> -> Document().also { doc -> value.forEach { (key, v) -> doc[key.toString()] = toBson(v) } }
            is Iterable<*> -> value.map { toBson(it) }
            is Array<*> -> value.map { toBson(it) }
            else -> value
        }
}
