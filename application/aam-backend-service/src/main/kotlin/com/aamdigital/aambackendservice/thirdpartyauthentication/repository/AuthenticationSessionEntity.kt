package com.aamdigital.aambackendservice.thirdpartyauthentication.repository

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.SourceType
import org.hibernate.annotations.UpdateTimestamp
import java.time.OffsetDateTime

/**
 * Represents an authentication session created when an external system sends a request
 * which is then available to be validated for passwordless login when our Keycloak third-party-authentication provider sends a request.
 */
@Entity
data class AuthenticationSessionEntity(
    /**
     * Internal ID (incremental for easier sorting and debugging)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    val id: Long = 0,

    /**
     * generated UUID used for communicating with the external system through API
     */
    @Column(unique = true)
    var externalIdentifier: String,

    /**
     * Keycloak User ID (in our Keycloak system)
     */
    @Column
    var userId: String,

    /**
     * User ID in the external system making the SSO request.
     * This is the userId send in the API request (see API Specs "UserSessionRequest").
     */
    @Column
    var externalUserId: String,

    @Column
    var sessionToken: String,

    @Column(length = 1000)
    var redirectUrl: String? = null,

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
