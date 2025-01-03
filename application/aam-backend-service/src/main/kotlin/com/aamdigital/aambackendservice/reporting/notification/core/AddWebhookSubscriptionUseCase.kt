package com.aamdigital.aambackendservice.reporting.notification.core

import com.aamdigital.aambackendservice.domain.DomainReference

interface AddWebhookSubscriptionUseCase {
    fun subscribe(report: DomainReference, webhook: DomainReference)
}
