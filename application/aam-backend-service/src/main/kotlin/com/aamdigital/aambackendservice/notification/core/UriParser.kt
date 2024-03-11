package com.aamdigital.aambackendservice.notification.core

interface UriParser {
    fun replacePlaceholder(url: String, values: Map<String, String>): String
}
