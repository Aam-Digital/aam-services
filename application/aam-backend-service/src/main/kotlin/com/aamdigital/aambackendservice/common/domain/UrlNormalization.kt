package com.aamdigital.aambackendservice.common.domain

/**
 * Ensures an absolute URL has an explicit protocol, defaulting to https:// when missing.
 */
fun normalizeUrlWithHttpsDefault(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isBlank()) {
        return ""
    }
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
}
