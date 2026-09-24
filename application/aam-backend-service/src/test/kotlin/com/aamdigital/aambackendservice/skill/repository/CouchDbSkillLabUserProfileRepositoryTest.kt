package com.aamdigital.aambackendservice.skill.repository

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.domain.TestErrorCode
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.skill.repository.CouchDbSkillLabUserProfileRepository.Companion.SKILL_USER_PROFILE_DATABASE
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime

class CouchDbSkillLabUserProfileRepositoryTest {
    private val couchDbClient = mock<CouchDbClient>()
    private val profiles = CouchDbSkillLabUserProfileRepository(couchDbClient, ObjectMapper())
    private val syncs = CouchDbSkillLabUserProfileSyncRepository(couchDbClient, ObjectMapper())

    private fun profile(importedAt: OffsetDateTime? = null) =
        SkillLabUserProfileEntity(
            externalIdentifier = "profile/1",
            fullName = "Max Muster",
            mobileNumber = null,
            email = null,
            skills = emptySet(),
            updatedAt = null,
            importedAt = importedAt
        )

    /** Replaces the database-generated timestamp columns of the former table. */
    @Test
    fun `sets both timestamps when a profile is first stored`() {
        val entity = profile()

        profiles.save(entity)

        assertThat(entity.latestSyncAt).isNotNull()
        assertThat(entity.importedAt).isEqualTo(entity.latestSyncAt)
        verify(couchDbClient).putDatabaseDocument(
            eq(SKILL_USER_PROFILE_DATABASE),
            eq("SkillProfile:profile%2F1"),
            same(entity)
        )
    }

    @Test
    fun `keeps the import time of a profile stored before`() {
        val importedAt = OffsetDateTime.parse("2024-01-01T00:00:00Z")
        val entity = profile(importedAt = importedAt)

        profiles.save(entity)

        assertThat(entity.importedAt).isEqualTo(importedAt)
        assertThat(entity.latestSyncAt).isAfter(importedAt)
    }

    @Test
    fun `finds no sync state for a project that was never synced`() {
        whenever(
            couchDbClient.getDatabaseDocument(
                eq(SKILL_USER_PROFILE_DATABASE),
                eq("SkillLabUserProfileSync:project-1"),
                any(),
                eq(SkillLabUserProfileSyncEntity::class)
            )
        ).thenThrow(NotFoundException(code = TestErrorCode.TEST_EXCEPTION))

        assertThat(syncs.findByProjectId("project-1")).isEmpty()
    }

    /** The sync state lives next to the profiles, so dropping that database resets both. */
    @Test
    fun `stores the sync state next to the profiles`() {
        val entity = SkillLabUserProfileSyncEntity(projectId = "project-1", latestSync = OffsetDateTime.now())

        syncs.save(entity)
        syncs.delete(entity)

        verify(couchDbClient).putDatabaseDocument(
            eq(SKILL_USER_PROFILE_DATABASE),
            eq("SkillLabUserProfileSync:project-1"),
            same(entity)
        )
        verify(couchDbClient).deleteDatabaseDocument(SKILL_USER_PROFILE_DATABASE, "SkillLabUserProfileSync:project-1")
    }
}
