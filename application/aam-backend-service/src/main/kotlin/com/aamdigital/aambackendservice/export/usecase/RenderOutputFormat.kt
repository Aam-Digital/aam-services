package com.aamdigital.aambackendservice.export.usecase

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * The file format a render request asks the template engine for, read from Carbone's `convertTo`
 * option, and the name of the generated file that follows from it.
 *
 * The format is not restricted to a list of known ones: which conversions are possible depends on
 * the template and on the engine, so the engine is left to reject what it cannot produce. It only
 * has to be a plain extension, because it becomes part of the file name.
 */
internal object RenderOutputFormat {
    const val PDF = "pdf"
    const val ZIP = "zip"

    const val INVALID_FORMAT_MESSAGE =
        "The 'convertTo' field must name a file format by its extension, e.g. \"pdf\" or \"docx\"."

    private val formatPattern = Regex("[a-z0-9]{1,10}")
    private val illegalFileNameCharacters = Regex("[\\\\/*?\"<>|]")

    /**
     * Extensions an admin may have put at the end of the file name pattern, which are replaced by
     * the extension of the format that is actually generated.
     */
    private val replaceableExtensions =
        setOf(PDF, "docx", "doc", "odt", "xlsx", "xls", "ods", "pptx", "ppt", "odp")

    /**
     * @return the requested format in lower case, [PDF] if the request does not name one, or null
     * if `convertTo` is not usable as a file extension.
     */
    fun fromRequestBody(bodyData: JsonNode): String? {
        val convertTo = bodyData.get("convertTo")
        if (convertTo == null || convertTo.isNull) return PDF

        val formatName =
            when {
                convertTo.isTextual -> convertTo.asText()

                // Carbone also accepts { "formatName": "pdf", "formatOptions": { ... } }
                convertTo.isObject -> convertTo.get("formatName")?.takeIf { it.isTextual }?.asText()

                else -> null
            }

        return formatName?.lowercase()?.takeIf { formatPattern.matches(it) }
    }

    /**
     * Sets the request's `convertTo` to [format], so the template engine produces exactly the
     * format the file is named after: also when the request named none (the engine would then
     * keep the template's own format) or wrote it in upper case. The object form keeps its
     * `formatOptions`.
     */
    fun applyTo(
        bodyData: ObjectNode,
        format: String
    ) {
        val convertTo = bodyData.get("convertTo")
        if (convertTo is ObjectNode) {
            convertTo.put("formatName", format)
        } else {
            bodyData.put("convertTo", format)
        }
    }

    /**
     * The template's file name pattern, safe for a file system and ending in the extension of the
     * generated [format], whichever document extension the pattern was configured with.
     */
    fun targetFileName(
        fileNamePattern: String,
        format: String
    ): String {
        val sanitized = fileNamePattern.replace(illegalFileNameCharacters, "_")
        val extension = sanitized.substringAfterLast('.', "").lowercase()
        val baseName =
            if (extension == format || extension in replaceableExtensions) {
                sanitized.substringBeforeLast('.')
            } else {
                sanitized
            }
        return "$baseName.$format"
    }
}
