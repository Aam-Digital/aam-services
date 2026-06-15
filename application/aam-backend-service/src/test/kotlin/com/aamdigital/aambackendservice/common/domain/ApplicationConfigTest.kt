package com.aamdigital.aambackendservice.common.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApplicationConfigTest {

    @Test
    fun `should keep https base URL unchanged`() {
        val config = ApplicationConfig(baseUrl = "https://app.test")

        assertThat(config.normalizedBaseUrl).isEqualTo("https://app.test")
    }

    @Test
    fun `should keep http base URL unchanged`() {
        val config = ApplicationConfig(baseUrl = "http://app.test")

        assertThat(config.normalizedBaseUrl).isEqualTo("http://app.test")
    }

    @Test
    fun `should prepend https when protocol is missing`() {
        val config = ApplicationConfig(baseUrl = "app.test")

        assertThat(config.normalizedBaseUrl).isEqualTo("https://app.test")
    }
}
