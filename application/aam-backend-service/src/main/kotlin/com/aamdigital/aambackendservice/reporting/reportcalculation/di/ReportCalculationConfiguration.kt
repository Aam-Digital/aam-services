package com.aamdigital.aambackendservice.reporting.reportcalculation.di

import com.aamdigital.aambackendservice.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.couchdb.core.DatabaseRequest
import com.aamdigital.aambackendservice.domain.FileStorage
import com.aamdigital.aambackendservice.reporting.domain.DataTransformation
import com.aamdigital.aambackendservice.reporting.notification.core.NotificationService
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationChangeUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.queue.RabbitMqReportCalculationEventPublisher
import com.aamdigital.aambackendservice.reporting.reportcalculation.storage.DefaultReportCalculationStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultCreateReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultReportCalculationChangeUseCase
import com.aamdigital.aambackendservice.reporting.transformation.SqlFromDateTransformation
import com.aamdigital.aambackendservice.reporting.transformation.SqlToDateTransformation
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ReportCalculationConfiguration {

    @Bean("report-calculation-database-request")
    fun reportCalculationDatabaseRequest(): DatabaseRequest = DatabaseRequest("report-calculation")

    @Bean
    fun defaultReportCalculationStorage(
        couchDbClient: CouchDbClient,
        fileStorage: FileStorage
    ): ReportCalculationStorage = DefaultReportCalculationStorage(couchDbClient, fileStorage)

    @Bean
    fun defaultReportCalculationChangeUseCase(
        reportCalculationStorage: ReportCalculationStorage,
        objectMapper: ObjectMapper,
        notificationService: NotificationService,
    ): ReportCalculationChangeUseCase =
        DefaultReportCalculationChangeUseCase(reportCalculationStorage, objectMapper, notificationService)

    @Bean
    fun defaultCreateReportCalculationUseCase(
        reportCalculationStorage: ReportCalculationStorage,
        reportCalculationEventPublisher: RabbitMqReportCalculationEventPublisher,
    ) = DefaultCreateReportCalculationUseCase(reportCalculationStorage, reportCalculationEventPublisher)

    @Bean
    fun getSqlFromDateTransformation(): DataTransformation<String> = SqlFromDateTransformation()

    @Bean
    fun getSqlToDateTransformation(): DataTransformation<String> = SqlToDateTransformation()

    @Bean
    fun getJsonFactory(
        objectMapper: ObjectMapper
    ): JsonFactory = JsonFactory().setCodec(objectMapper)
}
