package com.aamdigital.aambackendservice.reporting.notification.controller

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.HttpErrorDto
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.notification.core.AddWebhookSubscriptionUseCase
import com.aamdigital.aambackendservice.reporting.notification.core.CreateWebhookRequest
import com.aamdigital.aambackendservice.reporting.notification.core.NotificationStorage
import com.aamdigital.aambackendservice.reporting.notification.dto.Webhook
import com.aamdigital.aambackendservice.reporting.notification.dto.WebhookAuthenticationType
import com.aamdigital.aambackendservice.reporting.notification.dto.WebhookTarget
import com.aamdigital.aambackendservice.reporting.notification.storage.WebhookOwner
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
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
    private val addWebhookSubscriptionUseCase: AddWebhookSubscriptionUseCase,
) {

    @GetMapping
    fun fetchWebhooks(
        principal: Principal,
    ): ResponseEntity<Any> {
        val webhooks = try {
            notificationStorage.fetchAllWebhooks()
                .filter { webhook ->
                    webhook.owner.creator == principal.name
                }
                .map { webhook ->
                    mapToDto(webhook)
                }
        } catch (ex: Exception) {
            return when (ex) {
                is NotFoundException -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(
                        HttpErrorDto(
                            errorCode = "NOT_FOUND",
                            errorMessage = ex.localizedMessage,
                        )
                    )

                else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                        HttpErrorDto(
                            errorCode = "INTERNAL_SERVER_ERROR",
                            errorMessage = ex.localizedMessage,
                        )
                    )
            }
        }
        return ResponseEntity.ok(webhooks)
    }

    @GetMapping("/{webhookId}")
    fun fetchWebhook(
        @PathVariable webhookId: String,
        principal: Principal,
    ): ResponseEntity<Any> {
        val webhook = try {
            notificationStorage.fetchWebhook(DomainReference(webhookId))
        } catch (ex: Exception) {
            return when (ex) {
                is NotFoundException -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(
                        HttpErrorDto(
                            errorCode = "NOT_FOUND",
                            errorMessage = ex.localizedMessage,
                        )
                    )

                else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                        HttpErrorDto(
                            errorCode = "INTERNAL_SERVER_ERROR",
                            errorMessage = ex.localizedMessage,
                        )
                    )
            }
        }

        return if (webhook.owner.creator == principal.name) {
            ResponseEntity.ok(mapToDto(webhook))
        } else {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @PostMapping
    fun storeWebhook(
        @RequestBody request: CreateWebhookRequestDto,
        principal: Principal,
    ): ResponseEntity<Any> {
        val webhook = try {
            notificationStorage.createWebhook(
                CreateWebhookRequest(
                    user = principal.name,
                    label = request.label,
                    target = request.target,
                    authentication = request.authentication
                )
            )
        } catch (ex: Exception) {
            return when (ex) {
                is NotFoundException -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(
                        HttpErrorDto(
                            errorCode = "NOT_FOUND",
                            errorMessage = ex.localizedMessage,
                        )
                    )

                else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                        HttpErrorDto(
                            errorCode = "INTERNAL_SERVER_ERROR",
                            errorMessage = ex.localizedMessage,
                        )
                    )
            }
        }

        return ResponseEntity.ok(DomainReference(webhook.id))
    }

    @PostMapping("/{webhookId}/subscribe/report/{reportId}")
    fun registerReportNotification(
        @PathVariable webhookId: String,
        @PathVariable reportId: String,
    ): ResponseEntity<*> {
        addWebhookSubscriptionUseCase.subscribe(
            report = DomainReference(reportId),
            webhook = DomainReference(webhookId)
        )

        return ResponseEntity.ok().build<Any>()
    }

    @DeleteMapping("/{webhookId}/subscribe/report/{reportId}")
    fun unregisterReportNotification(
        @PathVariable webhookId: String,
        @PathVariable reportId: String,
    ): ResponseEntity<*> {
        notificationStorage.removeSubscription(
            DomainReference(webhookId),
            DomainReference(reportId)
        )

        return ResponseEntity.ok().build<Any>()
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
