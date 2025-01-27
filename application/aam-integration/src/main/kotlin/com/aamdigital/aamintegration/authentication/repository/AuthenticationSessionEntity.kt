package com.aamdigital.aamintegration.authentication.repository

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.SourceType
import org.hibernate.annotations.UpdateTimestamp
import java.time.OffsetDateTime

@Entity
data class AuthenticationSessionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    val id: Long = 0,

    @Column(unique = true)
    var externalIdentifier: String,

    @Column
    var userId: String,

    @Column
    var externalUserId: String,

    @Column
    var sessionToken: String,

//    @Column
//    var redirectUrl: String,

    @Column
    var validUntil: OffsetDateTime,

    @Column
    var usedAt: OffsetDateTime? = null,

    /** latest update within our system */
    @UpdateTimestamp(source = SourceType.DB)
    @Column(updatable = true)
    var updatedAt: OffsetDateTime? = null,

    /** original date (first latestSyncAt) when added to our system */
    @CreationTimestamp(source = SourceType.DB)
    @Column(updatable = true)
    var createdAt: OffsetDateTime? = null,
)
