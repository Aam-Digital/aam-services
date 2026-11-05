package com.aamdigital.aambackendservice.common.domain

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

/**
 * Texts configured in the frontend (e.g. labels and titles) can be multi-lingual:
 * either a plain string or an object of translations per locale,
 * e.g. `{"en-US": "Action", "de": "Aktion"}`.
 */
object TranslatableText {
    /** Locales preferred when picking one translation; matches the frontend's default language. */
    private val PREFERRED_LOCALES = listOf("en-US", "en")

    /**
     * Resolve a configured text to a single string.
     * A translation object resolves to the English text, else to any non-empty translation.
     *
     * @return null if the value is missing or not a text
     */
    fun resolve(node: JsonNode?): String? {
        if (node == null || node.isNull || node.isMissingNode) {
            return null
        }
        if (node.isTextual) {
            return node.textValue()
        }
        if (!node.isObject) {
            return null
        }
        val translations =
            node
                .properties()
                .filter { it.value.isTextual && it.value.textValue().isNotEmpty() }
                .associate { it.key to it.value.textValue() }
        return PREFERRED_LOCALES.firstNotNullOfOrNull { translations[it] } ?: translations.values.firstOrNull()
    }
}

/**
 * Deserializes a [TranslatableText] into a single string,
 * for DTO properties that the frontend may store as a translation object.
 * A value without any text (e.g. an empty translation object) becomes an empty string.
 */
class TranslatableTextDeserializer : StdDeserializer<String>(String::class.java) {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext
    ): String = TranslatableText.resolve(p.codec.readTree<JsonNode>(p)) ?: ""

    override fun getNullValue(ctxt: DeserializationContext): String? = null
}
