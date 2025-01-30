package com.aamdigital.aambackendservice.notification.core

import com.aamdigital.aambackendservice.domain.DomainUseCase
import com.aamdigital.aambackendservice.domain.UseCaseData
import com.aamdigital.aambackendservice.domain.UseCaseRequest

data class SyncNotificationConfigRequest(
    val notificationConfigDatabase: String,
    val notificationConfigId: String,
    val notificationConfigRev: String,
) : UseCaseRequest

data class SyncNotificationConfigData(
    val imported: Boolean,
    val updated: Boolean,
    val skipped: Boolean,
    val deleted: Boolean,
    val message: String,
) : UseCaseData

abstract class SyncNotificationConfigUseCase :
    DomainUseCase<SyncNotificationConfigRequest, SyncNotificationConfigData>()
