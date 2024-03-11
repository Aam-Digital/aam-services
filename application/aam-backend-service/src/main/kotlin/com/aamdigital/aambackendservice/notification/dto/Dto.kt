package com.aamdigital.aambackendservice.notification.dto

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.notification.storage.WebhookOwner

class WebhookAuthentication(
    var type: WebhookAuthenticationType,
    val secret: String,
)

data class WebhookTarget(
    val method: String,
    val url: String,
)

enum class WebhookAuthenticationType {
    API_KEY
}

data class Webhook(
    val id: String,
    val label: String,
    val target: WebhookTarget,
    val authentication: WebhookAuthentication,
    val owner: WebhookOwner,
    val reportSubscriptions: MutableList<DomainReference> = mutableListOf()
)
