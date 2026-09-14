package com.aamdigital.aambackendservice.reporting.webhook

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.webhook.storage.WebhookOwner
import java.time.Instant

data class Webhook(
    val id: String,
    val label: String,
    val target: WebhookTarget,
    val authentication: WebhookAuthentication,
    val owner: WebhookOwner,
    val reportSubscriptions: MutableList<DomainReference> = mutableListOf(),
    val createdAt: Instant? = null
)

data class WebhookTarget(
    val method: String,
    val url: String
)

class WebhookAuthentication(
    var type: WebhookAuthenticationType,
    val secret: String
)

enum class WebhookAuthenticationType {
    API_KEY
}
