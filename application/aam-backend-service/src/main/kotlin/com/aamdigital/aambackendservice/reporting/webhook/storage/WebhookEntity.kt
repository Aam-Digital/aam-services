package com.aamdigital.aambackendservice.reporting.webhook.storage

import com.aamdigital.aambackendservice.reporting.webhook.WebhookAuthenticationType
import com.aamdigital.aambackendservice.reporting.webhook.WebhookTarget
import java.time.Instant

data class WebhookAuthenticationEntity(
    var type: WebhookAuthenticationType,
    val iv: String,
    val data: String
)

data class WebhookOwner(
    val creator: String,
    val users: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
    val roles: List<String> = emptyList()
)

data class WebhookEntity(
    val id: String,
    val label: String,
    val target: WebhookTarget,
    val authentication: WebhookAuthenticationEntity,
    val owner: WebhookOwner,
    val reportSubscriptions: MutableList<String> = mutableListOf(),
    // nullable because documents created before this field existed have none
    val createdAt: Instant? = null
)
