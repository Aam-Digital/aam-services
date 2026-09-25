package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileEntity
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

/**
 * Behavioural tests for the profile search: they assert which profiles come back, not how the
 * store was queried. Asserting on the query arguments handed to the repository would pin the
 * query shape rather than the search.
 */
class InMemorySearchUserProfileUseCaseTest {
    private lateinit var repository: FakeSkillUserProfileRepository
    private lateinit var service: InMemorySearchUserProfileUseCase

    private class FakeSkillUserProfileRepository(
        private val profiles: MutableList<SkillLabUserProfileEntity> = mutableListOf()
    ) : SkillLabUserProfileRepository {
        override fun existsByExternalIdentifier(externalIdentifier: String) =
            profiles.any { it.externalIdentifier == externalIdentifier }

        override fun findByExternalIdentifier(externalIdentifier: String) =
            profiles.first { it.externalIdentifier == externalIdentifier }

        override fun findAll(): List<SkillLabUserProfileEntity> = profiles.toList()

        override fun save(entity: SkillLabUserProfileEntity) {
            profiles.removeIf { it.externalIdentifier == entity.externalIdentifier }
            profiles.add(entity)
        }
    }

    @BeforeEach
    fun setup() {
        repository = FakeSkillUserProfileRepository()
        service =
            InMemorySearchUserProfileUseCase(
                userProfileRepository = repository,
                objectMapper = Jackson2ObjectMapperBuilder().build()
            )
    }

    private fun givenProfile(
        id: String,
        fullName: String? = null,
        email: String? = null,
        phone: String? = null
    ) = repository.save(
        SkillLabUserProfileEntity(
            externalIdentifier = id,
            fullName = fullName,
            mobileNumber = phone,
            email = email,
            skills = emptySet(),
            updatedAt = null
        )
    )

    private fun search(
        fullName: String? = null,
        email: String? = null,
        phone: String? = null,
        page: Int = 1,
        pageSize: Int = 10
    ): SearchUserProfileData {
        val outcome =
            service.run(
                SearchUserProfileRequest(
                    fullName = fullName,
                    email = email,
                    phone = phone,
                    page = page,
                    pageSize = pageSize
                )
            )
        assertThat(outcome).isInstanceOf(UseCaseOutcome.Success::class.java)
        return (outcome as UseCaseOutcome.Success).data
    }

    @Test
    fun `matches an email address exactly and case-insensitively`() {
        // Given
        givenProfile(id = "1", fullName = "Max Muster", email = "example@mail.local", phone = "123")
        givenProfile(id = "2", fullName = "Other Person", email = "other@mail.local", phone = "456")

        // When
        val result = search(fullName = "Max Muster", email = "EXAMPLE@MAIL.LOCAL", phone = "123")

        // Then
        assertThat(result.result.map { it.id }).containsExactly("1")
        assertThat(result.totalElements).isEqualTo(1)
    }

    @Test
    fun `does not match a partial email address`() {
        // Given
        givenProfile(id = "1", email = "example@mail.local")

        // When
        val result = search(email = "example")

        // Then
        assertThat(result.result).isEmpty()
    }

    @Test
    fun `falls back to the phone number when no email matches`() {
        // Given
        givenProfile(id = "1", fullName = "Max Muster", email = "example@mail.local", phone = "0123456789")

        // When
        val result = search(fullName = "Nobody", email = "unknown@mail.local", phone = "456")

        // Then
        assertThat(result.result.map { it.id }).containsExactly("1")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `searches by phone number when the email is blank`(email: String) {
        // Given
        givenProfile(id = "1", phone = "0123456789")

        // When
        val result = search(email = email, phone = "0123")

        // Then
        assertThat(result.result.map { it.id }).containsExactly("1")
    }

    @Test
    fun `matches a substring of the full name`() {
        // Given
        givenProfile(id = "1", fullName = "Maximilian Muster")
        givenProfile(id = "2", fullName = "Someone Else")

        // When
        val result = search(fullName = "milian Mus")

        // Then
        assertThat(result.result.map { it.id }).containsExactly("1")
    }

    @Test
    fun `falls back to first and last name when the full name does not match`() {
        // Given
        givenProfile(id = "1", fullName = "Max Peter Muster")
        givenProfile(id = "2", fullName = "Max Other")
        givenProfile(id = "3", fullName = "Nobody Muster")
        givenProfile(id = "4", fullName = "Unrelated Person")

        // When
        val result = search(fullName = "Max Muster")

        // Then
        assertThat(result.result.map { it.id }).containsExactlyInAnyOrder("1", "2", "3")
        assertThat(result.totalElements).isEqualTo(3)
    }

    @Test
    fun `does not split a single-word name`() {
        // Given
        givenProfile(id = "1", fullName = "Max Muster")

        // When
        val result = search(fullName = "Unrelated")

        // Then
        assertThat(result.result).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `returns nothing when every search parameter is blank`(blank: String) {
        // Given
        givenProfile(id = "1", fullName = "Max Muster", email = "example@mail.local", phone = "123")

        // When
        val result = search(fullName = blank, email = blank, phone = blank)

        // Then
        assertThat(result.result).isEmpty()
        assertThat(result.totalElements).isZero()
        assertThat(result.totalPages).isZero()
    }

    @Test
    fun `reports honest totals across pages`() {
        // Given
        (1..5).forEach { givenProfile(id = "$it", fullName = "Common Name $it") }

        // When
        val firstPage = search(fullName = "Common Name", page = 1, pageSize = 2)
        val lastPage = search(fullName = "Common Name", page = 3, pageSize = 2)

        // Then
        assertThat(firstPage.result).hasSize(2)
        assertThat(firstPage.totalElements).isEqualTo(5)
        assertThat(firstPage.totalPages).isEqualTo(3)
        assertThat(lastPage.result).hasSize(1)
        assertThat(lastPage.totalElements).isEqualTo(5)
    }

    @Test
    fun `returns an empty page when the page offset exceeds the Int range`() {
        // Given
        (1..3).forEach { givenProfile(id = "$it", fullName = "Common Name $it") }

        // When
        val result = search(fullName = "Common Name", page = Int.MAX_VALUE, pageSize = 100)

        // Then
        assertThat(result.result).isEmpty()
        assertThat(result.totalElements).isEqualTo(3)
        assertThat(result.totalPages).isEqualTo(1)
    }
}
