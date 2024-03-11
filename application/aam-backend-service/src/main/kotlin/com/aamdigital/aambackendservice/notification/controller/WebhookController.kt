package com.aamdigital.aambackendservice.notification.controller

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.ForbiddenAccessException
import com.aamdigital.aambackendservice.notification.core.CreateWebhookRequest
import com.aamdigital.aambackendservice.notification.core.NotificationService
import com.aamdigital.aambackendservice.notification.core.NotificationStorage
import com.aamdigital.aambackendservice.notification.dto.Webhook
import com.aamdigital.aambackendservice.notification.dto.WebhookAuthenticationType
import com.aamdigital.aambackendservice.notification.dto.WebhookTarget
import com.aamdigital.aambackendservice.notification.storage.WebhookOwner
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.security.Principal

data class WebhookAuthenticationWriteDto(
    val type: WebhookAuthenticationType,
    val apiKey: String,
)

data class WebhookAuthenticationReadDto(
    val type: String,
)

data class WebhookDto(
    val id: String,
    val label: String,
    val target: WebhookTarget,
    val authentication: WebhookAuthenticationReadDto,
    val owner: WebhookOwner,
    val reportSubscriptions: MutableList<String>,
)

data class CreateWebhookRequestDto(
    val label: String,
    val target: WebhookTarget,
    val authentication: WebhookAuthenticationWriteDto,
)

@RestController
@RequestMapping("/v1/reporting/webhook")
@Validated
class WebhookController(
    private val notificationStorage: NotificationStorage,
    private val notificationService: NotificationService,
) {

    @GetMapping
    fun fetchWebhooks(
        principal: Principal,
    ): Mono<List<WebhookDto>> {
        return notificationStorage.fetchAllWebhooks().map { webhooks ->
            webhooks
                .filter {
                    it.owner.creator == principal.name
                }
                .map {
                    mapToDto(it)
                }
        }
    }

    @GetMapping("/{webhookId}")
    fun fetchWebhook(
        @PathVariable webhookId: String,
        principal: Principal,
    ): Mono<WebhookDto> {
        return notificationStorage.fetchWebhook(DomainReference(webhookId))
            .handle { webhook, sink ->
                if (webhook.owner.creator == principal.name) {
                    sink.next(mapToDto(webhook))
                } else {
                    sink.error(ForbiddenAccessException())
                }
            }
    }

    @PostMapping
    fun storeWebhook(
        @RequestBody request: CreateWebhookRequestDto,
        principal: Principal,
    ): Mono<DomainReference> {
        return notificationStorage.createWebhook(
            CreateWebhookRequest(
                user = principal.name,
                label = request.label,
                target = request.target,
                authentication = request.authentication
            )
        ).map {
            DomainReference(it.id)
        }
    }

    @PostMapping("/{webhookId}/subscribe/report/{reportId}")
    fun registerReportNotification(
        @PathVariable webhookId: String,
        @PathVariable reportId: String,
    ): Mono<Unit> {
        return notificationStorage.addSubscription(
            DomainReference(webhookId),
            DomainReference(reportId)
        ).map {
            notificationService.triggerWebhook(
                report = DomainReference(reportId),
                webhook = DomainReference(webhookId),
                reportCalculation = DomainReference("all")
            )
        }
    }

    @DeleteMapping("/{webhookId}/subscribe/report/{reportId}")
    fun unregisterReportNotification(
        @PathVariable webhookId: String,
        @PathVariable reportId: String,
    ): Mono<Unit> {
        return notificationStorage.removeSubscription(
            DomainReference(webhookId),
            DomainReference(reportId)
        )
    }

    private fun mapToDto(it: Webhook): WebhookDto {
        return WebhookDto(
            id = it.id,
            label = it.label,
            target = it.target,
            authentication = WebhookAuthenticationReadDto(
                type = it.authentication.type.toString()
            ),
            owner = it.owner,
            reportSubscriptions = it.reportSubscriptions.map { it.id }.toMutableList()
        )
    }
}
