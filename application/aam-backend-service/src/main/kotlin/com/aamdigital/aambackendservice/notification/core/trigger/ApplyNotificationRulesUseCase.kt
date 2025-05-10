package com.aamdigital.aambackendservice.notification.core.trigger

import com.aamdigital.aambackendservice.common.changes.domain.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseData
import com.aamdigital.aambackendservice.common.domain.UseCaseRequest

data class ApplyNotificationRulesRequest(
    val documentChangeEvent: DocumentChangeEvent,
) : UseCaseRequest

data class ApplyNotificationRulesData(
    val notificationsSendCount: Int
) : UseCaseData

abstract class ApplyNotificationRulesUseCase :
    DomainUseCase<ApplyNotificationRulesRequest, ApplyNotificationRulesData>()
