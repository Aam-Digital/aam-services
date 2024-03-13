package com.aamdigital.aambackendservice.reporting.notification.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.notification.controller.WebhookAuthenticationWriteDto
import com.aamdigital.aambackendservice.reporting.notification.dto.Webhook
import com.aamdigital.aambackendservice.reporting.notification.dto.WebhookTarget
import reactor.core.publisher.Mono

data class CreateWebhookRequest(
    val user: String,
    val label: String,
    val target: WebhookTarget,
    val authentication: WebhookAuthenticationWriteDto,
)

interface NotificationStorage {
    fun addSubscription(webhookRef: DomainReference, entityRef: DomainReference): Mono<Unit>
    fun removeSubscription(webhookRef: DomainReference, entityRef: DomainReference): Mono<Unit>
    fun fetchAllWebhooks(): Mono<List<Webhook>>
    fun fetchWebhook(webhookRef: DomainReference): Mono<Webhook>
    fun createWebhook(request: CreateWebhookRequest): Mono<DomainReference>
}
