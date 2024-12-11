package com.aamdigital.aambackendservice.skill.repository

import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.SourceType
import org.hibernate.annotations.UpdateTimestamp
import java.time.OffsetDateTime

@Entity
data class SkillLabUserProfileEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    val id: Long = 0,

    @Column(unique = true)
    var externalIdentifier: String,

    @Column
    var fullName: String?,

    @Column
    var mobileNumber: String?,

    @Column
    var email: String?,

    @ElementCollection(fetch = FetchType.EAGER)
    var skills: Set<SkillReferenceEntity>,

    /**
     * represents the latest update at skillLab
     */
    @Column
    var updatedAt: String?,

    /** latest update within our system */
    @UpdateTimestamp(source = SourceType.DB)
    @Column(updatable = true)
    var latestSyncAt: OffsetDateTime? = null,

    /** original date (first latestSyncAt) when added to our system */
    @CreationTimestamp(source = SourceType.DB)
    @Column(updatable = true)
    var importedAt: OffsetDateTime? = null,
)
