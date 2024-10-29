package com.aamdigital.aambackendservice.reporting.report.di

import com.aamdigital.aambackendservice.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.reporting.report.core.IdentifyAffectedReportsUseCase
import com.aamdigital.aambackendservice.reporting.report.core.ReportSchemaGenerator
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorage
import com.aamdigital.aambackendservice.reporting.report.core.SimpleReportSchemaGenerator
import com.aamdigital.aambackendservice.reporting.report.storage.DefaultReportStorage
import com.aamdigital.aambackendservice.reporting.report.usecase.DefaultIdentifyAffectedReportsUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ReportConfiguration {

    @Bean
    fun defaultReportStorage(
        couchDbClient: CouchDbClient,
        objectMapper: ObjectMapper,
    ): ReportStorage = DefaultReportStorage(
        couchDbClient = couchDbClient,
        objectMapper = objectMapper
    )

    @Bean
    fun defaultSimpleReportSchemaGenerator(): ReportSchemaGenerator = SimpleReportSchemaGenerator()

    @Bean
    fun defaultIdentifyAffectedReportsUseCase(
        reportStorage: ReportStorage,
        schemaGenerator: ReportSchemaGenerator,
    ): IdentifyAffectedReportsUseCase =
        DefaultIdentifyAffectedReportsUseCase(reportStorage, schemaGenerator)

}
