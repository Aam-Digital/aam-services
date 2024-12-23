package com.aamdigital.aambackendservice.notification.repositiory

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.SourceType
import java.time.OffsetDateTime

@Entity
data class NotificationConfigEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    val id: Long = 0,

    @Column
    var channelPush: Boolean,

    @Column
    var channelEmail: Boolean,

    @Column
    var revision: String,

    @Column(unique = true)
    var userIdentifier: String,

    @OneToMany(fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    var notificationRules: List<NotificationRuleEntity>,

    @CreationTimestamp(source = SourceType.DB)
    @Column
    var createdAt: OffsetDateTime? = null,

    @CreationTimestamp(source = SourceType.DB)
    @Column
    var updatedAt: OffsetDateTime? = null,
)
