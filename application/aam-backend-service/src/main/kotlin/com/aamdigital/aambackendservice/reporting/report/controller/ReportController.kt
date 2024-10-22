package com.aamdigital.aambackendservice.reporting.report.controller

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.HttpErrorDto
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.report.core.ReportingStorage
import com.aamdigital.aambackendservice.reporting.report.dto.ReportDto
import com.aamdigital.aambackendservice.reporting.storage.DefaultReportStorage
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/reporting/report")
@Validated
class ReportController(
    private val reportingStorage: ReportingStorage,
    private val reportStorage: DefaultReportStorage,
) {
    @GetMapping
    fun fetchReports(): ResponseEntity<Any> {
        val allReports = try {
            reportStorage.fetchAllReports("sql")
        } catch (ex: Exception) {
            return when (ex) {
                is NotFoundException -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(
                        HttpErrorDto(
                            errorCode = "NOT_FOUND",
                            errorMessage = ex.localizedMessage,
                        )
                    )

                else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                        HttpErrorDto(
                            errorCode = "INTERNAL_SERVER_ERROR",
                            errorMessage = ex.localizedMessage,
                        )
                    )
            }
        }

        val pendingCalculations = try {
            reportingStorage.fetchPendingCalculations()
        } catch (ex: Exception) {
            return when (ex) {
                is NotFoundException -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(
                        HttpErrorDto(
                            errorCode = "NOT_FOUND",
                            errorMessage = ex.localizedMessage,
                        )
                    )

                else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                        HttpErrorDto(
                            errorCode = "INTERNAL_SERVER_ERROR",
                            errorMessage = ex.localizedMessage,
                        )
                    )
            }
        }

        return ResponseEntity.ok(allReports.map { report ->
            ReportDto(
                id = report.id,
                name = report.name,
                schema = report.schema,
                calculationPending = pendingCalculations.any {
                    it.id == report.id
                }
            )
        })
    }

    @GetMapping("/{reportId}")
    fun fetchReport(
        @PathVariable reportId: String
    ): ResponseEntity<Any> {
        val reportOptional =
            reportStorage.fetchReport(DomainReference(id = reportId))

        if (reportOptional.isEmpty) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                    HttpErrorDto(
                        errorCode = "NOT_FOUND",
                        errorMessage = "Could not fetch report with id $reportId",
                    )
                )
        }

        val report = reportOptional.get()

        val pendingCalculations = try {
            reportingStorage.fetchPendingCalculations()
        } catch (ex: Exception) {
            return when (ex) {
                is NotFoundException -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(
                        HttpErrorDto(
                            errorCode = "NOT_FOUND",
                            errorMessage = ex.localizedMessage,
                        )
                    )

                else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                        HttpErrorDto(
                            errorCode = "INTERNAL_SERVER_ERROR",
                            errorMessage = ex.localizedMessage,
                        )
                    )
            }
        }

        return ResponseEntity.ok(ReportDto(
            id = report.id,
            name = report.name,
            schema = report.schema,
            calculationPending = pendingCalculations.any {
                it.id == report.id
            }
        ))
    }
}
