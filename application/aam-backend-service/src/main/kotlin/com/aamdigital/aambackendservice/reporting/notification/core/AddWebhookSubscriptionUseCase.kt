package com.aamdigital.aambackendservice.reporting.notification.core

import com.aamdigital.aambackendservice.domain.DomainReference
import reactor.core.publisher.Mono

interface AddWebhookSubscriptionUseCase {
    fun subscribe(report: DomainReference, webhook: DomainReference): Mono<Unit>
}
