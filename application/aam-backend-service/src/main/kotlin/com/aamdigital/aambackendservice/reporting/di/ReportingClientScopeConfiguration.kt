package com.aamdigital.aambackendservice.reporting.di

import com.aamdigital.aambackendservice.common.keycloak.core.ClientScopeRequest
import com.aamdigital.aambackendservice.reporting.ConditionalOnReportingEnabled
import com.aamdigital.aambackendservice.reporting.ReportingScopes
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Client scopes of the reporting API, ensured to exist in Keycloak on startup.
 *
 * API clients that only have them as Optional scope are switched to Default,
 * because integrations set up before these scopes were enforced may not request them explicitly.
 */
@Configuration
@ConditionalOnReportingEnabled
class ReportingClientScopeConfiguration {
    @Bean("reporting-read-client-scope-request")
    fun reportingReadClientScopeRequest(): ClientScopeRequest =
        ClientScopeRequest(
            name = ReportingScopes.READ,
            description = "Access to reporting-api GET endpoints",
            promoteOptionalToDefault = true
        )

    @Bean("reporting-write-client-scope-request")
    fun reportingWriteClientScopeRequest(): ClientScopeRequest =
        ClientScopeRequest(
            name = ReportingScopes.WRITE,
            description = "Access to reporting-api POST endpoints",
            promoteOptionalToDefault = true
        )
}
