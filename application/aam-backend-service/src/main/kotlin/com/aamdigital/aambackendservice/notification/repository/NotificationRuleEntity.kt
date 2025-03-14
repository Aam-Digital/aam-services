package com.aamdigital.aambackendservice.notification.repository

import com.aamdigital.aambackendservice.notification.domain.NotificationType
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity
data class NotificationRuleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    val id: Long = 0,

    @Column
    var label: String,

    @Column(unique = true)
    var externalIdentifier: String,

    @Column
    @Enumerated(EnumType.STRING)
    var notificationType: NotificationType,

    @Column
    var entityType: String,

    @Column
    var changeType: String,

    @ElementCollection(fetch = FetchType.EAGER)
    var conditions: List<NotificationConditionEntity>,

    @Column
    var enabled: Boolean,
)
