package com.aamdigital.aambackendservice.notification.repository

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.SourceType
import java.time.OffsetDateTime

@Entity
data class UserDeviceEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    val id: Long = 0,

    @Column
    var deviceName: String?,

    @Column(
        unique = true,
        nullable = false,
        updatable = false,
    )
    var deviceToken: String,

    @Column
    var userIdentifier: String,

    @CreationTimestamp(source = SourceType.DB)
    @Column
    var createdAt: OffsetDateTime? = null,
)
