package com.aamdigital.aambackendservice.notification.storage

import com.aamdigital.aambackendservice.notification.dto.WebhookAuthenticationType
import com.aamdigital.aambackendservice.notification.dto.WebhookTarget

data class WebhookAuthenticationEntity(
    var type: WebhookAuthenticationType,
    val iv: String,
    val data: String,
)

data class WebhookOwner(
    val creator: String,
    val users: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
    val roles: List<String> = emptyList(),
)

data class WebhookEntity(
    val id: String,
    val label: String,
    val target: WebhookTarget,
    val authentication: WebhookAuthenticationEntity,
    val owner: WebhookOwner,
    val reportSubscriptions: MutableList<String> = mutableListOf()
)
