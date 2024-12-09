package com.aamdigital.aambackendservice.skill.repository

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * Stores the latest successful SkillLabFetchUserProfileUpdateUseCase run date for each project.
 */
@Entity
@Table(indexes = [Index(columnList = "projectId", unique = true)])
data class SkillLabUserProfileSyncEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    var id: Long = 0,

    var projectId: String,
    var latestSync: OffsetDateTime,
)
