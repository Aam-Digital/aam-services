package com.aamdigital.aambackendservice.reporting.webhook.storage

import com.aamdigital.aambackendservice.crypto.core.CryptoService
import com.aamdigital.aambackendservice.crypto.core.EncryptedData
import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.webhook.Webhook
import com.aamdigital.aambackendservice.reporting.webhook.WebhookAuthentication
import java.util.*

class DefaultWebhookStorage(
    private val webhookRepository: WebhookRepository,
    private val cryptoService: CryptoService,
) : WebhookStorage {
    override fun addSubscription(webhookRef: DomainReference, entityRef: DomainReference) {
        val webhook = webhookRepository.fetchWebhook(
            webhookRef = webhookRef
        )

        if (webhook.reportSubscriptions.indexOf(entityRef.id) == -1) {
            webhook.reportSubscriptions.add(entityRef.id)
        }

        webhookRepository.storeWebhook(webhook)
    }

    override fun removeSubscription(webhookRef: DomainReference, entityRef: DomainReference) {
        val webhook = webhookRepository.fetchWebhook(
            webhookRef = webhookRef
        )
        webhook.reportSubscriptions.remove(entityRef.id)
        webhookRepository.storeWebhook(webhook)
    }

    override fun fetchAllWebhooks(): List<Webhook> {
        return webhookRepository.fetchAllWebhooks().map {
            mapFromEntity(it)
        }
    }

    override fun fetchWebhook(webhookRef: DomainReference): Webhook {
        return mapFromEntity(webhookRepository.fetchWebhook(webhookRef = webhookRef))
    }

    override fun createWebhook(request: CreateWebhookRequest): DomainReference {
        val encryptedKey = cryptoService.encrypt(request.authentication.apiKey)
        val newId = "Webhook:${UUID.randomUUID()}"

        webhookRepository.storeWebhook(
            webhook = WebhookEntity(
                id = newId,
                label = request.label,
                target = request.target,
                authentication = WebhookAuthenticationEntity(
                    type = request.authentication.type,
                    data = encryptedKey.data,
                    iv = encryptedKey.iv,
                ),
                owner = WebhookOwner(
                    creator = request.user,
                    roles = emptyList(),
                    users = emptyList(),
                    groups = emptyList(),
                ),
                reportSubscriptions = mutableListOf()
            )
        )

        return DomainReference(newId)
    }

    private fun mapFromEntity(entity: WebhookEntity): Webhook =
        Webhook(
            id = entity.id,
            label = entity.label,
            target = entity.target,
            authentication = WebhookAuthentication(
                type = entity.authentication.type,
                secret = cryptoService.decrypt(
                    EncryptedData(
                        iv = entity.authentication.iv,
                        data = entity.authentication.data
                    )
                ),
            ),
            owner = entity.owner,
            reportSubscriptions = entity.reportSubscriptions.map { DomainReference(it) }.toMutableList()
        )
}
