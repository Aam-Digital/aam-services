package com.aamdigital.aambackendservice.notification.core.create

import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseData
import com.aamdigital.aambackendservice.common.domain.UseCaseRequest
import com.aamdigital.aambackendservice.notification.core.CreateUserNotificationEvent

data class CreateNotificationRequest(
    val createUserNotificationEvent: CreateUserNotificationEvent,
) : UseCaseRequest

data class CreateNotificationData(
    val success: Boolean,
    val messageCreated: Boolean,
    val messageReference: String?,
) : UseCaseData

abstract class CreateNotificationUseCase : DomainUseCase<CreateNotificationRequest, CreateNotificationData>()
