package com.aamdigital.aambackendservice.admin.apiclient.core

import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatNoException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.Random

class NewApiClientTest {
    private val offeredScopes = setOf("reporting_read", "third_party_authentication")

    @ParameterizedTest
    @CsvSource(
        "'Tolä Data: Integration', 'api-tola-data-integration-[a-z0-9]{6}'",
        "'डेटा', 'api-[a-z0-9]{6}'"
    )
    fun `should derive the client id from the name, with a random suffix`(
        name: String,
        expectedPattern: String
    ) {
        // When
        val clientId = clientIdFor(name, Random(1))

        // Then
        assertThat(clientId).matches(expectedPattern)
    }

    @Test
    fun `should give API clients with the same name different client ids`() {
        // Given
        val random = Random(1)

        // When
        val clientIds = (1..2).map { clientIdFor("Same", random) }

        // Then
        assertThat(clientIds.distinct()).hasSize(2)
    }

    @Test
    fun `should accept a name and client scopes offered to API clients`() {
        assertThatNoException().isThrownBy {
            validateNewApiClient("TolaData", null, setOf("reporting_read", "third_party_authentication"), offeredScopes)
            validateNewApiClient("TolaData", "api-tola_data.v2", setOf("reporting_read"), offeredScopes)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "'', reporting_read, INVALID_NAME",
        "'   ', reporting_read, INVALID_NAME",
        "TolaData, '', NO_SCOPE",
        "TolaData, roles, UNKNOWN_SCOPE"
    )
    fun `should reject an invalid request`(
        name: String,
        scopes: String,
        expectedError: ApiClientAdministrationError
    ) {
        // Given
        val requestedScopes = scopes.split(",").filter { it.isNotBlank() }.toSet()

        // Then
        assertThatThrownBy { validateNewApiClient(name, null, requestedScopes, offeredScopes) }
            .isInstanceOf(InvalidArgumentException::class.java)
            .extracting("code")
            .isEqualTo(expectedError)
    }

    @ParameterizedTest
    @CsvSource(
        // other clients of the realm must not be chosen
        "app",
        "tola-data",
        // only after the prefix
        "api-",
        "api--x",
        "'api-with space'",
        "api-ümlaut"
    )
    fun `should reject a client id that is not an API client's`(clientId: String) {
        assertThatThrownBy { validateNewApiClient("TolaData", clientId, setOf("reporting_read"), offeredScopes) }
            .extracting("code")
            .isEqualTo(ApiClientAdministrationError.INVALID_CLIENT_ID)
    }

    @Test
    fun `should reject a client id that is too long`() {
        assertThatThrownBy {
            validateNewApiClient("TolaData", "api-" + "x".repeat(61), setOf("reporting_read"), offeredScopes)
        }.extracting("code").isEqualTo(ApiClientAdministrationError.INVALID_CLIENT_ID)
    }

    @Test
    fun `should reject a name that is too long`() {
        assertThatThrownBy {
            validateNewApiClient(
                "x".repeat(MAX_API_CLIENT_NAME_LENGTH + 1),
                null,
                setOf("reporting_read"),
                offeredScopes
            )
        }.extracting("code").isEqualTo(ApiClientAdministrationError.INVALID_NAME)
    }
}
