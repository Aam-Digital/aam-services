package com.aamdigital.aambackendservice.reporting.webhook.core

interface UriParser {
    fun replacePlaceholder(url: String, values: Map<String, String>): String
}
