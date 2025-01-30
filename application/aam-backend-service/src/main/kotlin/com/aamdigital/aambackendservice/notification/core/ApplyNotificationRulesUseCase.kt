package com.aamdigital.aambackendservice.notification.core

import com.aamdigital.aambackendservice.changes.domain.DocumentChangeEvent
import com.aamdigital.aambackendservice.domain.DomainUseCase
import com.aamdigital.aambackendservice.domain.UseCaseData
import com.aamdigital.aambackendservice.domain.UseCaseRequest

data class ApplyNotificationRulesRequest(
    val documentChangeEvent: DocumentChangeEvent,
) : UseCaseRequest

data class ApplyNotificationRulesData(
    val notificationsSendCount: Int
) : UseCaseData

abstract class ApplyNotificationRulesUseCase :
    DomainUseCase<ApplyNotificationRulesRequest, ApplyNotificationRulesData>()
