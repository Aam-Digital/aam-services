package com.aamdigital.aambackendservice.notification.repositiory

import org.springframework.data.repository.CrudRepository
import java.util.*

interface NotificationConfigRepository : CrudRepository<NotificationConfigEntity, Long> {
    fun findByUserIdentifier(userIdentifier: String): Optional<NotificationConfigEntity>
}
