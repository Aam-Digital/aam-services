package com.aamdigital.aambackendservice.reporting.report.di

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.reporting.report.core.IdentifyAffectedReportsUseCase
import com.aamdigital.aambackendservice.reporting.report.core.ReportQueryAnalyser
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorage
import com.aamdigital.aambackendservice.reporting.report.core.SimpleReportQueryAnalyser
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
    fun defaultSimpleReportSchemaGenerator(): ReportQueryAnalyser = SimpleReportQueryAnalyser()

    @Bean
    fun defaultIdentifyAffectedReportsUseCase(
        reportStorage: ReportStorage,
        schemaGenerator: ReportQueryAnalyser,
    ): IdentifyAffectedReportsUseCase =
        DefaultIdentifyAffectedReportsUseCase(reportStorage, schemaGenerator)

}
