package com.aamdigital.aambackendservice.notification.storage

import com.aamdigital.aambackendservice.crypto.core.CryptoService
import com.aamdigital.aambackendservice.crypto.core.EncryptedData
import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.notification.core.CreateWebhookRequest
import com.aamdigital.aambackendservice.notification.core.NotificationStorage
import com.aamdigital.aambackendservice.notification.dto.Webhook
import com.aamdigital.aambackendservice.notification.dto.WebhookAuthentication
import reactor.core.publisher.Mono
import java.util.*

class DefaultNotificationStorage(
    private val webhookRepository: WebhookRepository,
    private val cryptoService: CryptoService,
) : NotificationStorage {
    override fun addSubscription(webhookRef: DomainReference, entityRef: DomainReference): Mono<Unit> {
        return webhookRepository.fetchWebhook(
            webhookRef = webhookRef
        )
            .map { webhook ->
                if (webhook.reportSubscriptions.indexOf(entityRef.id) == -1) {
                    webhook.reportSubscriptions.add(entityRef.id)
                }
                webhook
            }
            .flatMap { webhook ->
                webhookRepository.storeWebhook(webhook)
            }
            .map { }
    }

    override fun removeSubscription(webhookRef: DomainReference, entityRef: DomainReference): Mono<Unit> {
        return webhookRepository.fetchWebhook(
            webhookRef = webhookRef
        )
            .map { document ->
                document.reportSubscriptions.remove(entityRef.id)
                document
            }
            .flatMap {
                webhookRepository.storeWebhook(it)
            }
            .map { }
    }

    override fun fetchAllWebhooks(): Mono<List<Webhook>> {
        return webhookRepository.fetchAllWebhooks().map { entities ->
            entities.map { mapFromEntity(it) }
        }
    }

    override fun fetchWebhook(webhookRef: DomainReference): Mono<Webhook> {
        return webhookRepository.fetchWebhook(webhookRef = webhookRef).map {
            mapFromEntity(it)
        }
    }

    override fun createWebhook(request: CreateWebhookRequest): Mono<DomainReference> {
        val encryptedKey = cryptoService.encrypt(request.authentication.apiKey)
        val newId = "Webhook:${UUID.randomUUID()}"

        return webhookRepository.storeWebhook(
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
        ).map {
            DomainReference(newId)
        }
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
