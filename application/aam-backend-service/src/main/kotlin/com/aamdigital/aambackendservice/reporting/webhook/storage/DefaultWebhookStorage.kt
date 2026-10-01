package com.aamdigital.aambackendservice.reporting.webhook.storage

import com.aamdigital.aambackendservice.common.crypto.core.CryptoService
import com.aamdigital.aambackendservice.common.crypto.core.EncryptedData
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.InternalServerException
import com.aamdigital.aambackendservice.reporting.webhook.Webhook
import com.aamdigital.aambackendservice.reporting.webhook.WebhookAuthentication
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

enum class WebhookError : AamErrorCode {
    INVALID_WEBHOOK_CONFIG
}

class DefaultWebhookStorage(
    private val webhookRepository: WebhookRepository,
    private val cryptoService: CryptoService,
    // this class is the only writer of the notification-webhook database, and that database is not
    // in database-change-detection.included-databases, so invalidating here is the only way the
    // subscription cache can learn about a write
    private val webhookSubscriptionCache: WebhookSubscriptionCache
) : WebhookStorage {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun addSubscription(
        webhookRef: DomainReference,
        entityRef: DomainReference
    ) {
        val webhook =
            webhookRepository.fetchWebhook(
                webhookRef = webhookRef
            )

        if (webhook.reportSubscriptions.indexOf(entityRef.id) == -1) {
            webhook.reportSubscriptions.add(entityRef.id)
        }

        webhookRepository.storeWebhook(webhook)
        webhookSubscriptionCache.invalidate()
    }

    override fun removeSubscription(
        webhookRef: DomainReference,
        entityRef: DomainReference
    ) {
        val webhook =
            webhookRepository.fetchWebhook(
                webhookRef = webhookRef
            )
        webhook.reportSubscriptions.remove(entityRef.id)
        webhookRepository.storeWebhook(webhook)
        webhookSubscriptionCache.invalidate()
    }

    override fun fetchAllWebhooks(): List<Webhook> {
        val webhooks = webhookRepository.fetchAllWebhooks()
        return webhooks
            .mapNotNull { mapFromEntity(it) }
    }

    override fun fetchWebhook(webhookRef: DomainReference): Webhook {
        val webhook = mapFromEntity(webhookRepository.fetchWebhook(webhookRef = webhookRef))
        if (webhook == null) {
            throw InternalServerException(
                "Error mapping Webhook entity ${webhookRef.id}",
                null,
                WebhookError.INVALID_WEBHOOK_CONFIG
            )
        }
        return webhook
    }

    override fun createWebhook(request: CreateWebhookRequest): DomainReference {
        val encryptedKey = cryptoService.encrypt(request.authentication.apiKey)
        val newId = "Webhook:${UUID.randomUUID()}"

        webhookRepository.storeWebhook(
            webhook =
                WebhookEntity(
                    id = newId,
                    label = request.label,
                    target = request.target,
                    authentication =
                        WebhookAuthenticationEntity(
                            type = request.authentication.type,
                            data = encryptedKey.data,
                            iv = encryptedKey.iv
                        ),
                    owner =
                        WebhookOwner(
                            creator = request.user,
                            roles = emptyList(),
                            users = emptyList(),
                            groups = emptyList()
                        ),
                    reportSubscriptions = mutableListOf(),
                    createdAt = Instant.now()
                )
        )
        // a new webhook has no subscriptions yet, so this is not strictly required today; it keeps
        // the invariant "every write through this class invalidates", which is what makes the
        // cache safe to reason about
        webhookSubscriptionCache.invalidate()

        return DomainReference(newId)
    }

    private fun mapFromEntity(entity: WebhookEntity): Webhook? {
        try {
            val authentication =
                WebhookAuthentication(
                    type = entity.authentication.type,
                    secret =
                        cryptoService.decrypt(
                            EncryptedData(
                                iv = entity.authentication.iv,
                                data = entity.authentication.data
                            )
                        )
                )
            val reportSubscriptions = entity.reportSubscriptions.map { DomainReference(it) }.toMutableList()
            return Webhook(
                id = entity.id,
                label = entity.label,
                target = entity.target,
                authentication,
                owner = entity.owner,
                reportSubscriptions,
                createdAt = entity.createdAt
            )
        } catch (ex: Exception) {
            logger.error("Could not map webhook entity ${entity.id} to Webhook object: ${ex.message}", ex)
            return null
        }
    }
}
