package com.aamdigital.aambackendservice.reporting.report.storage

import com.fasterxml.jackson.databind.JsonNode

private const val PREFERRED_LOCALE = "en-US"
private const val FALLBACK_LANGUAGE = "en"

/**
 * Turns the `title` of a ReportConfig document into the one text the backend shows.
 *
 * The title is either a plain string or, if it is configured in several languages, a map of
 * locale id to text, e.g. `{"en-US": "Basic Report", "de": "Basis-Bericht"}`. The backend has no
 * user locale, so a map resolves to its `en-US` text, else to the first English one (e.g. `en`
 * or `en-GB`), else to the first text of any language. Blank texts are skipped.
 * A missing title, or one of any other type, resolves to an empty text.
 */
fun resolveReportTitle(title: JsonNode?): String =
    when {
        title == null -> ""
        title.isTextual -> title.textValue()
        title.isObject -> resolveTranslatedTitle(title)
        else -> ""
    }

private fun resolveTranslatedTitle(translations: JsonNode): String {
    val texts =
        translations
            .properties()
            .filter { (_, text) -> text.isTextual && text.textValue().isNotBlank() }
            .map { (locale, text) -> locale to text.textValue() }

    return texts.firstOrNull { (locale, _) -> locale == PREFERRED_LOCALE }?.second
        ?: texts.firstOrNull { (locale, _) -> baseLanguage(locale) == FALLBACK_LANGUAGE }?.second
        ?: texts.firstOrNull()?.second
        ?: ""
}

private fun baseLanguage(locale: String): String = locale.substringBefore('-').substringBefore('_').lowercase()
