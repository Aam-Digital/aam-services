package com.aamdigital.aambackendservice.e2e.contract

/**
 * Shared helpers for comparing API operations across the spec, the e2e calls, and
 * the controller code. Path templates are normalized so that parameter names don't
 * matter: `/report/{reportId}` and `/report/{id}` both become `/report/{}`.
 */

internal fun isTemplateSegment(segment: String): Boolean = segment.startsWith("{") && segment.endsWith("}")

internal fun pathSegments(path: String): List<String> = path.trim('/').split('/').filter { it.isNotEmpty() }

internal fun normalizePath(path: String): String =
    "/" + pathSegments(path).joinToString("/") { if (isTemplateSegment(it)) "{}" else it }

/** A canonical key for an operation, e.g. `GET /report/{}`. */
internal fun operationKey(
    method: String,
    path: String
): String = "${method.uppercase()} ${normalizePath(path)}"

/** Whether a concrete request path (e.g. `/report/ReportConfig:1`) matches a spec template. */
internal fun pathMatchesTemplate(
    template: String,
    concretePath: String
): Boolean {
    val templateSegments = pathSegments(template)
    val actualSegments = pathSegments(concretePath)
    if (templateSegments.size != actualSegments.size) return false
    return templateSegments
        .zip(actualSegments)
        .all { (t, a) -> isTemplateSegment(t) || t == a }
}
