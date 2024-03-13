package com.aamdigital.aambackendservice.reporting.reportcalculation.controller

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.InternalServerException
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.domain.ReportData
import com.aamdigital.aambackendservice.reporting.report.core.ReportingStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationResult
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationUseCase
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/v1/reporting")
@Validated
class ReportCalculationController(
    private val reportingStorage: ReportingStorage,
    private val createReportCalculationUseCase: CreateReportCalculationUseCase
) {
    @PostMapping("/report-calculation/report/{reportId}")
    fun startCalculation(
        @PathVariable reportId: String
    ): Mono<DomainReference> {
        return reportingStorage.fetchReport(DomainReference(id = reportId))
            .flatMap { reportOptional ->
                val report = reportOptional.orElseThrow {
                    NotFoundException()
                }

                createReportCalculationUseCase.startReportCalculation(
                    CreateReportCalculationRequest(
                        report = DomainReference(report.id)
                    )
                ).handle { result, sink ->
                    when (result) {
                        is CreateReportCalculationResult.Failure -> {
                            sink.error(InternalServerException())
                        }

                        is CreateReportCalculationResult.Success -> sink.next(result.calculation)
                    }
                }
            }
    }

    @GetMapping("/report-calculation/report/{reportId}")
    fun fetchReportCalculations(
        @PathVariable reportId: String
    ): Mono<List<ReportCalculation>> {
        return reportingStorage.fetchCalculations(DomainReference(id = reportId))
    }

    @GetMapping("/report-calculation/{calculationId}")
    fun fetchReportCalculation(
        @PathVariable calculationId: String
    ): Mono<ReportCalculation> {
        return reportingStorage.fetchCalculation(DomainReference(id = calculationId))
            .map { calculationOptional ->
                val calculation = calculationOptional.orElseThrow {
                    NotFoundException()
                }

                // TODO Auth check

                calculation
            }
    }

    @GetMapping("/report-calculation/{calculationId}/data")
    fun fetchReportCalculationData(
        @PathVariable calculationId: String
    ): Mono<ReportData> {
        return reportingStorage.fetchData(DomainReference(id = calculationId))
            .map { calculationOptional ->
                val calculation = calculationOptional.orElseThrow {
                    NotFoundException()
                }

                // TODO Auth check

                calculation
            }
    }

}
