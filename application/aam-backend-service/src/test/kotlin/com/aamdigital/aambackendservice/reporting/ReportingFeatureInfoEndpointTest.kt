package com.aamdigital.aambackendservice.reporting

import com.aamdigital.aambackendservice.common.actuator.FeatureRegistrar
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.Test

/**
 * Verifies that the reporting feature endpoint participates in /actuator/features
 * only when features.reporting.enabled=true.
 */
class ReportingFeatureInfoEndpointTest {
    private val runner =
        ApplicationContextRunner()
            .withUserConfiguration(ReportingFeatureInfoEndpoint::class.java)

    @Test
    fun `endpoint is registered when reporting is enabled`() {
        runner
            .withPropertyValues("features.reporting.enabled=true")
            .run { context ->
                assertThat(context).hasSingleBean(ReportingFeatureInfoEndpoint::class.java)
                val registrar = context.getBean(ReportingFeatureInfoEndpoint::class.java) as FeatureRegistrar
                assertThat(registrar.getFeatureInfo().first).isEqualTo("reporting")
                assertThat(registrar.getFeatureInfo().second.enabled).isTrue
            }
    }

    @Test
    fun `endpoint is not registered when reporting is disabled`() {
        runner
            .withPropertyValues("features.reporting.enabled=false")
            .run { context ->
                assertThat(context).doesNotHaveBean(ReportingFeatureInfoEndpoint::class.java)
            }
    }

    @Test
    fun `endpoint is not registered when reporting flag is missing`() {
        runner.run { context ->
            assertThat(context).doesNotHaveBean(ReportingFeatureInfoEndpoint::class.java)
        }
    }
}
