package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.skill.repository.SkillUserProfile
import com.aamdigital.aambackendservice.skill.repository.SkillUserProfileRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

/**
 * Behavioural tests for the profile search: they assert which profiles come back, not how the
 * store was queried. The version they replace asserted on the JPA Example/ExampleMatcher arguments
 * handed to the repository, which pinned the query shape rather than the search.
 */
class InMemorySearchUserProfileUseCaseTest {
    private lateinit var repository: FakeSkillUserProfileRepository
    private lateinit var service: InMemorySearchUserProfileUseCase

    private class FakeSkillUserProfileRepository(
        private val profiles: MutableList<SkillUserProfile> = mutableListOf()
    ) : SkillUserProfileRepository {
        override fun findByExternalIdentifier(externalIdentifier: String) =
            profiles.firstOrNull { it.externalIdentifier == externalIdentifier }

        override fun findAll(): List<SkillUserProfile> = profiles.toList()

        override fun save(profile: SkillUserProfile) {
            profiles.removeIf { it.externalIdentifier == profile.externalIdentifier }
            profiles.add(profile)
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
        SkillUserProfile(
            externalIdentifier = id,
            fullName = fullName,
            mobileNumber = phone,
            email = email,
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
        givenProfile(id = "1", fullName = "Max Muster", email = "example@mail.local", phone = "123")
        givenProfile(id = "2", fullName = "Other Person", email = "other@mail.local", phone = "456")

        val result = search(fullName = "Max Muster", email = "EXAMPLE@MAIL.LOCAL", phone = "123")

        assertThat(result.result.map { it.id }).containsExactly("1")
        assertThat(result.totalElements).isEqualTo(1)
    }

    @Test
    fun `does not match a partial email address`() {
        givenProfile(id = "1", email = "example@mail.local")

        assertThat(search(email = "example").result).isEmpty()
    }

    @Test
    fun `falls back to the phone number when no email matches`() {
        givenProfile(id = "1", fullName = "Max Muster", email = "example@mail.local", phone = "0123456789")

        val result = search(fullName = "Nobody", email = "unknown@mail.local", phone = "456")

        assertThat(result.result.map { it.id }).containsExactly("1")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `searches by phone number when the email is blank`(email: String) {
        givenProfile(id = "1", phone = "0123456789")

        assertThat(search(email = email, phone = "0123").result.map { it.id }).containsExactly("1")
    }

    @Test
    fun `matches a substring of the full name`() {
        givenProfile(id = "1", fullName = "Maximilian Muster")
        givenProfile(id = "2", fullName = "Someone Else")

        assertThat(search(fullName = "milian Mus").result.map { it.id }).containsExactly("1")
    }

    @Test
    fun `falls back to first and last name when the full name does not match`() {
        givenProfile(id = "1", fullName = "Max Peter Muster")
        givenProfile(id = "2", fullName = "Max Other")
        givenProfile(id = "3", fullName = "Nobody Muster")
        givenProfile(id = "4", fullName = "Unrelated Person")

        val result = search(fullName = "Max Muster")

        assertThat(result.result.map { it.id }).containsExactlyInAnyOrder("1", "2", "3")
        assertThat(result.totalElements).isEqualTo(3)
    }

    @Test
    fun `does not split a single-word name`() {
        givenProfile(id = "1", fullName = "Max Muster")

        assertThat(search(fullName = "Unrelated").result).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " "])
    fun `returns nothing when every search parameter is blank`(blank: String) {
        givenProfile(id = "1", fullName = "Max Muster", email = "example@mail.local", phone = "123")

        val result = search(fullName = blank, email = blank, phone = blank)

        assertThat(result.result).isEmpty()
        assertThat(result.totalElements).isZero()
        assertThat(result.totalPages).isZero()
    }

    @Test
    fun `reports honest totals across pages`() {
        (1..5).forEach { givenProfile(id = "$it", fullName = "Common Name $it") }

        val firstPage = search(fullName = "Common Name", page = 1, pageSize = 2)
        val lastPage = search(fullName = "Common Name", page = 3, pageSize = 2)

        assertThat(firstPage.result).hasSize(2)
        assertThat(firstPage.totalElements).isEqualTo(5)
        assertThat(firstPage.totalPages).isEqualTo(3)
        assertThat(lastPage.result).hasSize(1)
        assertThat(lastPage.totalElements).isEqualTo(5)
    }
}
