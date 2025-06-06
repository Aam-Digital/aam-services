package com.aamdigital.aambackendservice.reporting.webhook.core

class DefaultUriParser : UriParser {
    override fun replacePlaceholder(url: String, values: Map<String, String>): String {
        var modifiedUrl = url

        for ((key, value) in values) {
            modifiedUrl = modifiedUrl.replace("<$key>", value)
        }

        return modifiedUrl
    }
}
