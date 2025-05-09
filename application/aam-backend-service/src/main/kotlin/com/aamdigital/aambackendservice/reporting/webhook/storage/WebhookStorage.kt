package com.aamdigital.aambackendservice.reporting.webhook.storage

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.webhook.Webhook
import com.aamdigital.aambackendservice.reporting.webhook.WebhookTarget
import com.aamdigital.aambackendservice.reporting.webhook.controller.WebhookAuthenticationWriteDto

data class CreateWebhookRequest(
    val user: String,
    val label: String,
    val target: WebhookTarget,
    val authentication: WebhookAuthenticationWriteDto,
)

interface WebhookStorage {
    fun addSubscription(webhookRef: DomainReference, entityRef: DomainReference)
    fun removeSubscription(webhookRef: DomainReference, entityRef: DomainReference)
    fun fetchAllWebhooks(): List<Webhook>
    fun fetchWebhook(webhookRef: DomainReference): Webhook
    fun createWebhook(request: CreateWebhookRequest): DomainReference
}
