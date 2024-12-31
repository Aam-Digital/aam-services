package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.skill.core.SqlSearchUserProfileUseCase.Companion.MATCHER_EMAIL
import com.aamdigital.aambackendservice.skill.core.SqlSearchUserProfileUseCase.Companion.MATCHER_NAME
import com.aamdigital.aambackendservice.skill.core.SqlSearchUserProfileUseCase.Companion.MATCHER_PHONE
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileEntity
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Example
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class SqlSearchUserProfileUseCaseTest {
    private lateinit var service: SqlSearchUserProfileUseCase

    @Mock
    private lateinit var userProfileRepository: SkillLabUserProfileRepository

    @BeforeEach
    fun setup() {
        reset(userProfileRepository)
        service = SqlSearchUserProfileUseCase(
            userProfileRepository = userProfileRepository,
            objectMapper = Jackson2ObjectMapperBuilder().build()
        )
    }


    @Test
    fun `should return exact match for email address`() {
        // given
        whenever(
            userProfileRepository.findAll(
                eq(
                    Example.of(
                        getSkillLabUserProfileEntity(
                            fullName = "Max Muster",
                            mobileNumber = "123",
                            email = "example@mail.local",
                        ),
                        MATCHER_EMAIL
                    )
                ),
                any<Pageable>()
            )
        ).thenReturn(
            PageImpl(
                listOf(
                    getSkillLabUserProfileEntity(
                        fullName = "Max Muster",
                        mobileNumber = null,
                        email = "example@mail.local",
                    )
                )
            )
        )

        // when
        val response = service.run(
            SearchUserProfileRequest(
                email = "example@mail.local",
                fullName = "Max Muster",
                phone = "123",
                page = 1,
                pageSize = 50,
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        verify(
            userProfileRepository,
            times(1)
        ).findAll(
            any<Example<SkillLabUserProfileEntity>>(),
            any<Pageable>()
        )

        assertEquals(1, (response as UseCaseOutcome.Success).data.totalElements)
    }

    @Test
    fun `should return exact match for mobileNumber when email check returns no match`() {
        val exampleEntity = getSkillLabUserProfileEntity(
            fullName = "Max Muster",
            mobileNumber = "123456789",
            email = "foo@mail.local",
        )

        val responseEntity = getSkillLabUserProfileEntity(
            fullName = "Max Muster",
            mobileNumber = "123456789",
            email = "example@mail.local",
        )

        // given
        whenever(
            userProfileRepository.findAll(
                eq(
                    Example.of(
                        exampleEntity,
                        MATCHER_EMAIL
                    )
                ),
                any<Pageable>()
            )
        ).thenReturn(Page.empty())

        whenever(
            userProfileRepository.findAll(
                eq(
                    Example.of(
                        exampleEntity,
                        MATCHER_PHONE
                    )
                ),
                any<Pageable>()
            )
        ).thenReturn(
            PageImpl(listOf(responseEntity))
        )

        // when
        val response = service.run(
            SearchUserProfileRequest(
                email = "foo@mail.local",
                fullName = "Max Muster",
                phone = "123456789",
                page = 1,
                pageSize = 50,
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        verify(
            userProfileRepository,
            times(2)
        ).findAll(
            any<Example<SkillLabUserProfileEntity>>(),
            any<Pageable>()
        )

        assertEquals(1, (response as UseCaseOutcome.Success).data.totalElements)
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["null", "", "    "],
    )
    fun `should return exact match for mobileNumber when email is nullOrBlank`(emailRawValue: String?) {
        val emailValue = if (emailRawValue == "null") {
            null
        } else {
            emailRawValue
        }

        val exampleEntity = getSkillLabUserProfileEntity(
            fullName = "Max Muster",
            mobileNumber = "123456789",
            email = emailValue,
        )

        val responseEntity = getSkillLabUserProfileEntity(
            fullName = "Max Muster",
            mobileNumber = "123456789",
            email = "example@mail.local",
        )

        // given
        whenever(
            userProfileRepository.findAll(
                eq(
                    Example.of(
                        exampleEntity,
                        MATCHER_PHONE
                    )
                ),
                any<Pageable>()
            )
        ).thenReturn(
            PageImpl(listOf(responseEntity))
        )

        // when
        val response = service.run(
            SearchUserProfileRequest(
                email = emailValue,
                fullName = "Max Muster",
                phone = "123456789",
                page = 1,
                pageSize = 50,
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        verify(
            userProfileRepository,
            times(1)
        ).findAll(
            any<Example<SkillLabUserProfileEntity>>(),
            any<Pageable>()
        )

        assertEquals(1, (response as UseCaseOutcome.Success).data.totalElements)
    }

    @Test
    fun `should return matches for fullName`() {
        val exampleEntity = getSkillLabUserProfileEntity(
            fullName = "Max Muster",
            mobileNumber = null,
            email = null,
        )

        val responseEntity = getSkillLabUserProfileEntity(
            fullName = "Max Muster",
            mobileNumber = "123456789",
            email = "example@mail.local",
        )

        // given
        whenever(
            userProfileRepository.findAll(
                eq(
                    Example.of(
                        exampleEntity,
                        MATCHER_NAME
                    )
                ),
                any<Pageable>()
            )
        ).thenReturn(
            PageImpl(listOf(responseEntity))
        )

        // when
        val response = service.run(
            SearchUserProfileRequest(
                email = null,
                fullName = "Max Muster",
                phone = null,
                page = 1,
                pageSize = 50,
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        verify(
            userProfileRepository,
            times(1)
        ).findAll(
            any<Example<SkillLabUserProfileEntity>>(),
            any<Pageable>()
        )

        assertEquals(1, (response as UseCaseOutcome.Success).data.totalElements)
    }

    @Test
    fun `should return matches for split fullName`() {
        val responseEntity = getSkillLabUserProfileEntity(
            fullName = "Max Muster",
            mobileNumber = "123456789",
            email = "example@mail.local",
        )

        // given
        whenever(
            userProfileRepository.findAll(
                eq(
                    Example.of(
                        getSkillLabUserProfileEntity(
                            fullName = "Max Martin Muster",
                            mobileNumber = null,
                            email = null,
                        ),
                        MATCHER_NAME
                    )
                ),
                any<Pageable>()
            )
        ).thenReturn(Page.empty())

        whenever(
            userProfileRepository.findAll(
                eq(
                    Example.of(
                        getSkillLabUserProfileEntity(
                            fullName = "Max",
                            mobileNumber = null,
                            email = null,
                        ),
                        MATCHER_NAME
                    )
                ),
                any<Pageable>()
            )
        ).thenReturn(
            PageImpl(listOf(responseEntity))
        )

        whenever(
            userProfileRepository.findAll(
                eq(
                    Example.of(
                        getSkillLabUserProfileEntity(
                            fullName = "Muster",
                            mobileNumber = null,
                            email = null,
                        ),
                        MATCHER_NAME
                    )
                ),
                any<Pageable>()
            )
        ).thenReturn(
            PageImpl(listOf(responseEntity))
        )

        // when
        val response = service.run(
            SearchUserProfileRequest(
                email = null,
                fullName = "Max Martin Muster",
                phone = null,
                page = 1,
                pageSize = 50,
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        verify(
            userProfileRepository,
            times(3)
        ).findAll(
            any<Example<SkillLabUserProfileEntity>>(),
            any<Pageable>()
        )

        assertEquals(1, (response as UseCaseOutcome.Success).data.totalElements)
    }

    @Test
    fun `should skip namePart search if not enough nameParts`() {
        // given
        whenever(
            userProfileRepository.findAll(
                eq(
                    Example.of(
                        getSkillLabUserProfileEntity(
                            fullName = "Mila",
                            mobileNumber = null,
                            email = null,
                        ),
                        MATCHER_NAME
                    )
                ),
                any<Pageable>()
            )
        ).thenReturn(Page.empty())

        // when
        val response = service.run(
            SearchUserProfileRequest(
                email = null,
                fullName = "Mila",
                phone = null,
                page = 1,
                pageSize = 50,
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        verify(
            userProfileRepository,
            times(1)
        ).findAll(
            any<Example<SkillLabUserProfileEntity>>(),
            any<Pageable>()
        )

        assertEquals(0, (response as UseCaseOutcome.Success).data.totalElements)
    }


    @ParameterizedTest
    @ValueSource(
        strings = ["null", "", "    "],
    )
    fun `should return empty list when all search parameter are empty`(rawValue: String?) {
        val emptyOrBlankValue = if (rawValue == "null") {
            null
        } else {
            rawValue
        }

        // when
        val response = service.run(
            SearchUserProfileRequest(
                email = emptyOrBlankValue,
                fullName = emptyOrBlankValue,
                phone = emptyOrBlankValue,
                page = 1,
                pageSize = 50,
            )
        )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        verify(
            userProfileRepository,
            times(0)
        ).findAll(
            any<Example<SkillLabUserProfileEntity>>(),
            any<Pageable>()
        )

        assertEquals(0, (response as UseCaseOutcome.Success).data.totalElements)
    }

    private fun getSkillLabUserProfileEntity(
        externalIdentifier: String = "",
        fullName: String,
        mobileNumber: String?,
        email: String?,
    ): SkillLabUserProfileEntity = SkillLabUserProfileEntity(
        id = 0,
        externalIdentifier = externalIdentifier,
        fullName = fullName,
        mobileNumber = mobileNumber,
        email = email,
        skills = emptySet(),
        updatedAt = "",
        latestSyncAt = null,
        importedAt = null,
    )
}
