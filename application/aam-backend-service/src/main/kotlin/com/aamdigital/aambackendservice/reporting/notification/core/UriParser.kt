package com.aamdigital.aambackendservice.reporting.notification.core

interface UriParser {
    fun replacePlaceholder(url: String, values: Map<String, String>): String
}
