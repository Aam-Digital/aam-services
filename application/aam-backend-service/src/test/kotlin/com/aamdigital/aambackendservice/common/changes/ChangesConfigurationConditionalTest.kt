package com.aamdigital.aambackendservice.common.changes

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.mock
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.Test

/**
 * Verifies that change-detection auto-activates whenever at least one consumer feature
 * (reporting or notification) is enabled, and turns off when both are disabled.
 * Also verifies that [CouchDbChangesPollingJob] follows the processor (via @ConditionalOnBean).
 */
class ChangesConfigurationConditionalTest {
    private val runner =
        ApplicationContextRunner()
            .withUserConfiguration(
                ChangesConfiguration::class.java,
                CouchDbChangesPollingJob::class.java,
            )
            .withBean(CouchDbClient::class.java, { mock<CouchDbClient>() })
            .withBean(ChangeEventPublisher::class.java, { mock<ChangeEventPublisher>() })
            .withBean(SyncRepository::class.java, { mock<SyncRepository>() })
            .withBean(ObjectMapper::class.java, { ObjectMapper() })
            .withBean(ChangeDetectionProperties::class.java, { ChangeDetectionProperties() })

    @Test
    fun `change-detection is active when reporting is enabled`() {
        runner
            .withPropertyValues(
                "features.reporting.enabled=true",
                "features.notification-api.enabled=false"
            )
            .run { context ->
                assertThat(context).hasSingleBean(CouchDbChangesProcessor::class.java)
                assertThat(context).hasSingleBean(CouchDbChangesPollingJob::class.java)
            }
    }

    @Test
    fun `change-detection is active when notification is enabled`() {
        runner
            .withPropertyValues(
                "features.reporting.enabled=false",
                "features.notification-api.enabled=true"
            )
            .run { context ->
                assertThat(context).hasSingleBean(CouchDbChangesProcessor::class.java)
                assertThat(context).hasSingleBean(CouchDbChangesPollingJob::class.java)
            }
    }

    @Test
    fun `change-detection is inactive when both consumers are disabled`() {
        runner
            .withPropertyValues(
                "features.reporting.enabled=false",
                "features.notification-api.enabled=false"
            )
            .run { context ->
                assertThat(context).doesNotHaveBean(CouchDbChangesProcessor::class.java)
                assertThat(context).doesNotHaveBean(CouchDbChangesPollingJob::class.java)
            }
    }

    @Test
    fun `change-detection is inactive when no flags are set`() {
        runner.run { context ->
            assertThat(context).doesNotHaveBean(CouchDbChangesProcessor::class.java)
            assertThat(context).doesNotHaveBean(CouchDbChangesPollingJob::class.java)
        }
    }
}
