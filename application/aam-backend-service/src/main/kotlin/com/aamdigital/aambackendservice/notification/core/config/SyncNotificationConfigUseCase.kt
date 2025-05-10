package com.aamdigital.aambackendservice.notification.core.config

import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseData
import com.aamdigital.aambackendservice.common.domain.UseCaseRequest

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
