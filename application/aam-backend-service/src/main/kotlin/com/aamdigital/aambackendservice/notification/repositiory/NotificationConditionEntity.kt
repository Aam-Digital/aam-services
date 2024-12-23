package com.aamdigital.aambackendservice.notification.repositiory

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class NotificationConditionEntity(
    @Column
    var field: String,

    @Column
    var operator: String,

    @Column
    var value: String,
)
