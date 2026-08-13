package com.aamdigital.aambackendservice.common.storage.di

import com.zaxxer.hikari.HikariConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.io.ClassPathResource

/**
 * Guards the datasource timeout settings in the shipped `application.yaml`.
 *
 * `socketTimeout` is passed straight through to the PostgreSQL driver, which makes it fragile in
 * two ways that are invisible in review:
 *
 * 1. pgjdbc property names are case-sensitive. If relaxed binding ever lowercased the map key,
 *    the driver would silently ignore `sockettimeout` and the timeout would be gone.
 * 2. pgjdbc expresses it in **seconds**, while every neighbouring HikariCP setting is in
 *    milliseconds. `30000` would look right and mean 8.3 hours.
 *
 * This binds the real configuration file the same way Spring Boot binds `spring.datasource.hikari`,
 * so either mistake fails the build rather than shipping a timeout that never fires.
 */
class DatasourceTimeoutConfigurationTest {
    private fun bindHikariConfig(): HikariConfig {
        val baseDocument =
            YamlPropertySourceLoader()
                .load("application.yaml", ClassPathResource("application.yaml"))
                .first() // the second document is the local-development profile

        val propertySources = MutablePropertySources().apply { addFirst(baseDocument) }

        return Binder(ConfigurationPropertySources.from(propertySources))
            .bind("spring.datasource.hikari", HikariConfig::class.java)
            .orElseThrow { AssertionError("no spring.datasource.hikari configuration found") }
    }

    @Test
    fun `passes socketTimeout to the driver with its case and seconds unit intact`() {
        // Given / When
        val socketTimeout = bindHikariConfig().dataSourceProperties.getProperty("socketTimeout")

        // Then - seconds, not milliseconds
        assertThat(socketTimeout).isEqualTo("30")
    }

    @Test
    fun `relies on HikariCP defaults for every timeout that is already bounded`() {
        // Given / When
        val hikariConfig = bindHikariConfig()

        // Then - these are deliberately not configured; asserting the defaults documents why, and
        // catches a dependency upgrade that changes them (keepaliveTime moved from disabled to
        // 2 minutes in HikariCP 6.3, which is what made the original report look actionable).
        assertThat(hikariConfig.keepaliveTime).isEqualTo(120_000)
        assertThat(hikariConfig.maxLifetime).isEqualTo(1_800_000)
        assertThat(hikariConfig.connectionTimeout).isEqualTo(30_000)
        assertThat(hikariConfig.validationTimeout).isEqualTo(5_000)
    }
}
