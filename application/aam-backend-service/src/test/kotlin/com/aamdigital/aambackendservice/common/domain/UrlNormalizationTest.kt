package com.aamdigital.aambackendservice.common.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UrlNormalizationTest {

    @Test
    fun `should return empty string for blank input`() {
        assertThat(normalizeUrlWithHttpsDefault("   ")).isEqualTo("")
    }

    @Test
    fun `should keep https URL unchanged`() {
        assertThat(normalizeUrlWithHttpsDefault("https://app.test")).isEqualTo("https://app.test")
    }

    @Test
    fun `should keep http URL unchanged`() {
        assertThat(normalizeUrlWithHttpsDefault("http://app.test")).isEqualTo("http://app.test")
    }

    @Test
    fun `should prepend https when protocol is missing`() {
        assertThat(normalizeUrlWithHttpsDefault("app.test")).isEqualTo("https://app.test")
    }
}
