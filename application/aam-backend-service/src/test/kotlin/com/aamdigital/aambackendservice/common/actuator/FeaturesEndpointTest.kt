package com.aamdigital.aambackendservice.common.actuator

import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

/**
 * Verifies that [FeaturesEndpoint] builds the response from registrars, nesting
 * sub-features (dotted names like `notification.email`) under their parent feature.
 */
class FeaturesEndpointTest {

    private fun registrar(name: String, enabled: Boolean = true) = object : FeatureRegistrar {
        override fun getFeatureInfo() = name to FeaturesInfoDto(enabled)
    }

    @Test
    fun `flat features are reported as top-level entries`() {
        val result = FeaturesEndpoint(
            listOf(registrar("export"), registrar("reporting")),
        ).getFeatureStatus()

        assertThat(result).isEqualTo(
            mapOf(
                "export" to mapOf("enabled" to true),
                "reporting" to mapOf("enabled" to true),
            ),
        )
    }

    @Test
    fun `dotted feature name is nested under its parent feature`() {
        val result = FeaturesEndpoint(
            listOf(registrar("notification"), registrar("notification.email")),
        ).getFeatureStatus()

        assertThat(result).isEqualTo(
            mapOf(
                "notification" to mapOf(
                    "enabled" to true,
                    "email" to mapOf("enabled" to true),
                ),
            ),
        )
    }

    @Test
    fun `nesting is independent of registrar order`() {
        val result = FeaturesEndpoint(
            // sub-feature registered before its parent
            listOf(registrar("notification.email"), registrar("notification")),
        ).getFeatureStatus()

        assertThat(result).isEqualTo(
            mapOf(
                "notification" to mapOf(
                    "enabled" to true,
                    "email" to mapOf("enabled" to true),
                ),
            ),
        )
    }

    @Test
    fun `no registrars yields an empty map`() {
        assertThat(FeaturesEndpoint(emptyList()).getFeatureStatus()).isEmpty()
    }
}
