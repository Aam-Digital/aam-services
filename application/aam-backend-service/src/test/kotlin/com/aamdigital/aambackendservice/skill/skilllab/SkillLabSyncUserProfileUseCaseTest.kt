package com.aamdigital.aambackendservice.skill.skilllab

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.skill.core.SyncUserProfileRequest
import com.aamdigital.aambackendservice.skill.repository.SkillReference
import com.aamdigital.aambackendservice.skill.repository.SkillUserProfile
import com.aamdigital.aambackendservice.skill.repository.SkillUserProfileRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.io.IOException
import java.time.Instant
import java.util.*

@ExtendWith(MockitoExtension::class)
class SkillLabSyncUserProfileUseCaseTest {
    private lateinit var service: SkillLabSyncUserProfileUseCase
    private lateinit var repository: FakeSkillUserProfileRepository

    @Mock
    lateinit var skillLabClient: SkillLabClient

    /** Asserts on what ends up stored rather than on how the store was called. */
    private class FakeSkillUserProfileRepository : SkillUserProfileRepository {
        val stored = mutableMapOf<String, SkillUserProfile>()
        var failOnSave: Exception? = null

        override fun findByExternalIdentifier(externalIdentifier: String) = stored[externalIdentifier]

        override fun findAll(): List<SkillUserProfile> = stored.values.toList()

        override fun save(profile: SkillUserProfile) {
            failOnSave?.let { throw it }
            stored[profile.externalIdentifier] = profile
        }
    }

    private val expectedSkills =
        listOf(
            SkillReference(
                externalIdentifier = "00000000-0000-0000-0000-000000000001",
                escoUri = "http://link-to-esco-skill-1",
                usage = "always"
            ),
            SkillReference(
                externalIdentifier = "00000000-0000-0000-0000-000000000002",
                escoUri = "http://link-to-esco-skill-2",
                usage = "always"
            ),
            SkillReference(
                externalIdentifier = "00000000-0000-0000-0000-000000000003",
                escoUri = "http://link-to-esco-skill-3",
                usage = "always"
            )
        )

    @BeforeEach
    fun setUp() {
        reset(skillLabClient)
        repository = FakeSkillUserProfileRepository()
        service =
            SkillLabSyncUserProfileUseCase(
                skillLabClient = skillLabClient,
                skillUserProfileRepository = repository,
                objectMapper = Jackson2ObjectMapperBuilder().build()
            )
    }

    private fun sync() =
        service.run(
            SyncUserProfileRequest(
                userProfile = DomainReference("user-profile-1"),
                project = DomainReference("project-1")
            )
        )

    @Test
    fun `should store new user profile and return Success`() {
        // given
        whenever(skillLabClient.fetchUserProfile(any())).thenReturn(
            getSkillLabProfileResponseDto("user-profile-1", mobileNumber = "")
        )

        // when
        val response = sync()

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        val stored = repository.stored.getValue("user-profile-1")
        assertThat(stored.fullName).isEqualTo("Max Muster")
        assertThat(stored.email).isEqualTo("max.muster@fake.local")
        // a blank number is stored unchanged
        assertThat(stored.mobileNumber).isEqualTo("")
        assertThat(stored.updatedAt).isEqualTo("2022-02-02T22:22Z")
        assertThat(stored.skills).isEqualTo(expectedSkills)
        assertThat(stored.latestSyncAt).isNotNull()
        assertThat(stored.importedAt).isEqualTo(stored.latestSyncAt)
    }

    @Test
    fun `should strip spaces and dashes from the mobile number`() {
        // given
        whenever(skillLabClient.fetchUserProfile(any())).thenReturn(
            getSkillLabProfileResponseDto("user-profile-1")
        )

        // when
        val response = sync()

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)
        assertThat(repository.stored.getValue("user-profile-1").mobileNumber).isEqualTo("+49123456789")
    }

    @Test
    fun `should update existing user profile and keep its importedAt`() {
        // given
        val importedAt = Instant.parse("2020-01-01T00:00:00Z")
        repository.stored["user-profile-1"] =
            SkillUserProfile(
                externalIdentifier = "user-profile-1",
                fullName = "Old Name",
                mobileNumber = "+490000",
                email = "old@fake.local",
                skills = emptyList(),
                updatedAt = "2020-01-01T00:00Z",
                latestSyncAt = importedAt,
                importedAt = importedAt
            )
        whenever(skillLabClient.fetchUserProfile(any())).thenReturn(
            getSkillLabProfileResponseDto("user-profile-1")
        )

        // when
        val response = sync()

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        val stored = repository.stored.getValue("user-profile-1")
        assertThat(stored.fullName).isEqualTo("Max Muster")
        assertThat(stored.skills).isEqualTo(expectedSkills)
        assertThat(stored.importedAt).isEqualTo(importedAt)
        assertThat(stored.latestSyncAt).isAfter(importedAt)
    }

    @Test
    fun `should and return Failure when database access throws Exception`() {
        // given
        whenever(skillLabClient.fetchUserProfile(any())).thenReturn(
            getSkillLabProfileResponseDto("user-profile-1")
        )
        repository.failOnSave = IOException("mock-error")

        // when
        val response = sync()

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        Assertions.assertEquals(
            SkillLabSyncUserProfileErrorCode.IO_ERROR,
            (response as UseCaseOutcome.Failure).errorCode
        )
        Assertions.assertEquals("mock-error", response.errorMessage)
    }

    private fun getSkillLabProfileResponseDto(
        id: String = "user-profile-1",
        mobileNumber: String = " +49 1234-56789 "
    ): SkillLabProfileResponseDto =
        SkillLabProfileResponseDto(
            profile =
                SkillLabProfileDto(
                    id = id,
                    mobileNumber = mobileNumber,
                    city = "Berlin",
                    country = "DE",
                    projects = listOf("Project 1", "Project 2", "Project 3"),
                    fullName = "Max Muster",
                    email = "max.muster@fake.local",
                    streetAndHouseNumber = "",
                    arrivalInCountry = "",
                    nationality = "DE",
                    gender = "male",
                    birthday = "2000-01-01",
                    genderCustom = null,
                    experiences =
                        listOf(
                            SkillLabExperienceDto(
                                experiencesSkills =
                                    mutableListOf(
                                        SkillLabSkillDto(
                                            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                                            externalId = "http://link-to-esco-skill-1",
                                            choice = "always"
                                        ),
                                        SkillLabSkillDto(
                                            id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                                            externalId = "http://link-to-esco-skill-2",
                                            choice = "always"
                                        ),
                                        SkillLabSkillDto(
                                            id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
                                            externalId = "http://link-to-esco-skill-3",
                                            choice = "always"
                                        )
                                    )
                            )
                        ),
                    updatedAt = "2022-02-02T22:22Z"
                )
        )
}
