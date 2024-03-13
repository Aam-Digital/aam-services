package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.InvalidArgumentException
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.domain.ReportData
import com.aamdigital.aambackendservice.reporting.report.core.QueryStorage
import com.aamdigital.aambackendservice.reporting.report.core.ReportingStorage
import com.aamdigital.aambackendservice.reporting.report.sqs.QueryRequest
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.*
import kotlin.jvm.optionals.getOrDefault

@Service
class DefaultReportCalculator(
    private val reportingStorage: ReportingStorage,
    private val queryStorage: QueryStorage,
) : ReportCalculator {
    override fun calculate(reportCalculation: ReportCalculation): Mono<ReportData> {
        return reportingStorage.fetchReport(reportCalculation.report)
            .flatMap { reportOptional ->
                val report = reportOptional.getOrDefault(null)
                    ?: return@flatMap Mono.error(NotFoundException())

                if (report.mode != "sql") {
                    return@flatMap Mono.error(InvalidArgumentException())
                }

                queryStorage.executeQuery(
                    query = QueryRequest(query = report.query)
                )
                    .map { queryResult ->
                        ReportData(
                            id = "ReportData:${UUID.randomUUID()}",
                            report = reportCalculation.report,
                            calculation = DomainReference(reportCalculation.id),
                            data = queryResult.result
                        )
                    }
            }
    }
}
