package com.aamdigital.aambackendservice.reporting.report.controller

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.report.core.ReportingStorage
import com.aamdigital.aambackendservice.reporting.report.dto.ReportDto
import com.aamdigital.aambackendservice.reporting.storage.DefaultReportStorage
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1/reporting/report")
@Validated
class ReportController(
    private val reportingStorage: ReportingStorage,
    private val reportStorage: DefaultReportStorage,
) {
    @GetMapping
    fun fetchReports(): Mono<List<ReportDto>> {
        return reportStorage.fetchAllReports("sql")
            .zipWith(
                reportingStorage.fetchPendingCalculations()
            ).map { results ->
                val reports = results.t1
                val calculations = results.t2
                reports.map { report ->
                    ReportDto(
                        id = report.id,
                        name = report.name,
                        schema = report.schema,
                        calculationPending = calculations.any {
                            it.id == report.id
                        }
                    )
                }
            }
    }

    @GetMapping("/{reportId}")
    fun fetchReport(
        @PathVariable reportId: String
    ): Mono<ReportDto> {
        return reportStorage
            .fetchReport(DomainReference(id = reportId))
            .zipWith(
                reportingStorage.fetchPendingCalculations()
            ).map { results ->
                val reportOptional = results.t1
                val calculations = results.t2

                val report = reportOptional.orElseThrow {
                    NotFoundException()
                }

                ReportDto(
                    id = report.id,
                    name = report.name,
                    schema = report.schema,
                    calculationPending = calculations.any {
                        it.id == report.id
                    }
                )
            }
    }
}
