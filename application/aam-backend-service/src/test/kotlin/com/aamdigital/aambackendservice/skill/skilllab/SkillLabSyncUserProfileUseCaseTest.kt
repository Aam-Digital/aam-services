package com.aamdigital.aambackendservice.skill.skilllab

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.skill.core.SyncUserProfileRequest
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileEntity
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileRepository
import com.aamdigital.aambackendservice.skill.repository.SkillReferenceEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.io.IOException
import java.util.*

@ExtendWith(MockitoExtension::class)
class SkillLabSyncUserProfileUseCaseTest {
    private lateinit var service: SkillLabSyncUserProfileUseCase

    @Mock
    lateinit var skillLabClient: SkillLabClient

    @Mock
    lateinit var skillLabUserProfileRepository: SkillLabUserProfileRepository

    @BeforeEach
    fun setUp() {
        reset(
            skillLabClient,
            skillLabUserProfileRepository,
        )
        service = SkillLabSyncUserProfileUseCase(
            skillLabClient = skillLabClient,
            skillLabUserProfileRepository = skillLabUserProfileRepository,
            objectMapper = Jackson2ObjectMapperBuilder().build(),
        )
    }

    @Test
    fun `should store new user profile and return Success`() {
        // given
        whenever(skillLabClient.fetchUserProfile(any())).thenReturn(
            getSkillLabProfileResponseDto("user-profile-1", mobileNumber = "")
        )
        whenever(skillLabUserProfileRepository.existsByExternalIdentifier(eq("user-profile-1"))).thenReturn(false)

        // when
        val response = service.run(
            SyncUserProfileRequest(
                userProfile = DomainReference("user-profile-1"),
                project = DomainReference("project-1"),
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        verify(
            skillLabUserProfileRepository,
            times(1)
        ).save(
            eq(
                SkillLabUserProfileEntity(
                    id = 0L,
                    externalIdentifier = "user-profile-1",
                    fullName = "Max Muster",
                    mobileNumber = "",
                    email = "max.muster@fake.local",
                    skills = setOf(
                        SkillReferenceEntity(
                            externalIdentifier = "00000000-0000-0000-0000-000000000001",
                            escoUri = "http://link-to-esco-skill-1",
                            usage = "always"
                        ),
                        SkillReferenceEntity(
                            externalIdentifier = "00000000-0000-0000-0000-000000000002",
                            escoUri = "http://link-to-esco-skill-2",
                            usage = "always"
                        ),
                        SkillReferenceEntity(
                            externalIdentifier = "00000000-0000-0000-0000-000000000003",
                            escoUri = "http://link-to-esco-skill-3",
                            usage = "always"
                        )
                    ),
                    updatedAt = "2022-02-02T22:22Z",
                    latestSyncAt = null,
                    importedAt = null
                )
            )
        )
    }

    @Test
    fun `should update existing user profile  and return Success`() {
        val existingEntity = SkillLabUserProfileEntity(
            id = 0L,
            externalIdentifier = "user-profile-1",
            fullName = "Max Muster",
            mobileNumber = "+49123456789",
            email = "max.muster@fake.local",
            skills = setOf(
                SkillReferenceEntity(
                    externalIdentifier = "00000000-0000-0000-0000-000000000001",
                    escoUri = "http://link-to-esco-skill-1",
                    usage = "always"
                ),
                SkillReferenceEntity(
                    externalIdentifier = "00000000-0000-0000-0000-000000000002",
                    escoUri = "http://link-to-esco-skill-2",
                    usage = "always"
                ),
                SkillReferenceEntity(
                    externalIdentifier = "00000000-0000-0000-0000-000000000003",
                    escoUri = "http://link-to-esco-skill-3",
                    usage = "always"
                )
            ),
            updatedAt = "2022-02-02T22:22Z",
            latestSyncAt = null,
            importedAt = null
        )

        // given
        whenever(skillLabClient.fetchUserProfile(any())).thenReturn(
            getSkillLabProfileResponseDto("user-profile-1")
        )
        whenever(skillLabUserProfileRepository.existsByExternalIdentifier(eq("user-profile-1"))).thenReturn(true)
        whenever(skillLabUserProfileRepository.findByExternalIdentifier(eq("user-profile-1")))
            .thenReturn(
                existingEntity
            )


        // when
        val response = service.run(
            SyncUserProfileRequest(
                userProfile = DomainReference("user-profile-1"),
                project = DomainReference("project-1"),
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        verify(
            skillLabUserProfileRepository,
            times(1)
        ).save(
            eq(existingEntity)
        )
    }

    @Test
    fun `should and return Failure when database access throws Exception`() {
        // given
        whenever(skillLabClient.fetchUserProfile(any())).thenReturn(
            getSkillLabProfileResponseDto("user-profile-1")
        )
        whenever(skillLabUserProfileRepository.existsByExternalIdentifier(eq("user-profile-1"))).thenReturn(false)

        whenever(
            skillLabUserProfileRepository.save(
                eq(
                    SkillLabUserProfileEntity(
                        id = 0L,
                        externalIdentifier = "user-profile-1",
                        fullName = "Max Muster",
                        mobileNumber = "+49123456789",
                        email = "max.muster@fake.local",
                        skills = setOf(
                            SkillReferenceEntity(
                                externalIdentifier = "00000000-0000-0000-0000-000000000001",
                                escoUri = "http://link-to-esco-skill-1",
                                usage = "always"
                            ),
                            SkillReferenceEntity(
                                externalIdentifier = "00000000-0000-0000-0000-000000000002",
                                escoUri = "http://link-to-esco-skill-2",
                                usage = "always"
                            ),
                            SkillReferenceEntity(
                                externalIdentifier = "00000000-0000-0000-0000-000000000003",
                                escoUri = "http://link-to-esco-skill-3",
                                usage = "always"
                            )
                        ),
                        updatedAt = "2022-02-02T22:22Z",
                        latestSyncAt = null,
                        importedAt = null
                    )
                )

            )
        ).thenAnswer {
            throw IOException("mock-error")
        }

        // when
        val response = service.run(
            SyncUserProfileRequest(
                userProfile = DomainReference("user-profile-1"),
                project = DomainReference("project-1"),
            )
        )

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
        mobileNumber: String = " +49 1234-56789 ",
    ): SkillLabProfileResponseDto =
        SkillLabProfileResponseDto(
            profile = SkillLabProfileDto(
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
                experiences = listOf(
                    SkillLabExperienceDto(
                        experiencesSkills = mutableListOf(
                            SkillLabSkillDto(
                                id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                                externalId = "http://link-to-esco-skill-1",
                                choice = "always",
                            ),
                            SkillLabSkillDto(
                                id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                                externalId = "http://link-to-esco-skill-2",
                                choice = "always",
                            ),
                            SkillLabSkillDto(
                                id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
                                externalId = "http://link-to-esco-skill-3",
                                choice = "always",
                            )
                        )
                    )
                ),
                updatedAt = "2022-02-02T22:22Z",
            )
        )
}
