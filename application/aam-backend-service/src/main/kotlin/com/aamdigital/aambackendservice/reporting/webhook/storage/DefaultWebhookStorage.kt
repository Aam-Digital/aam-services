package com.aamdigital.aambackendservice.reporting.webhook.storage

import com.aamdigital.aambackendservice.common.crypto.core.CryptoService
import com.aamdigital.aambackendservice.common.crypto.core.EncryptedData
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.webhook.Webhook
import com.aamdigital.aambackendservice.reporting.webhook.WebhookAuthentication
import org.slf4j.LoggerFactory
import java.util.*

class DefaultWebhookStorage(
    private val webhookRepository: WebhookRepository,
    private val cryptoService: CryptoService,
) : WebhookStorage {
    private val logger = LoggerFactory.getLogger(javaClass)

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
        val webhooks = webhookRepository.fetchAllWebhooks();
        val mappedWebhooks = webhooks
            .map { mapFromEntity(it) }
            .filterNotNull()
        return mappedWebhooks
    }

    override fun fetchWebhook(webhookRef: DomainReference): Webhook {
        val webhook = mapFromEntity(webhookRepository.fetchWebhook(webhookRef = webhookRef));
        if (webhook == null) {
            throw Error("Error mapping Webhook entity ${webhookRef.id}")
        }
        return webhook
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

    private fun mapFromEntity(entity: WebhookEntity): Webhook? {
        try {
            val authentication = WebhookAuthentication(
                type = entity.authentication.type,
                secret = cryptoService.decrypt(
                    EncryptedData(
                        iv = entity.authentication.iv,
                        data = entity.authentication.data
                    )
                ),
            )
            val reportSubscriptions = entity.reportSubscriptions.map { DomainReference(it) }.toMutableList()
            return Webhook(
                id = entity.id,
                label = entity.label,
                target = entity.target,
                authentication,
                owner = entity.owner,
                reportSubscriptions
            )
        } catch (ex: Exception) {
            logger.error("Could not map webhook entity ${entity.id} to Webhook object: ${ex.message}", ex)
            return null
        }
    }
}
