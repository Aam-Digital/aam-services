package com.aamdigital.aambackendservice.reporting.report.controller

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.error.HttpErrorDto
import com.aamdigital.aambackendservice.error.InvalidArgumentException
import com.aamdigital.aambackendservice.error.NetworkException
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.domain.ReportSchema
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorage
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * This is the interface shared to external users of the API endpoints.
 */
data class ReportDto(
    val id: String,
    val name: String,
    val schema: ReportSchema?,
)

@RestController
@RequestMapping("/v1/reporting/report")
@Validated
class ReportController(
    private val reportStorage: ReportStorage,
) {
    @GetMapping
    fun fetchReports(): ResponseEntity<Any> {
        val allReports = try {
            reportStorage.fetchAllReports("sql")
        } catch (ex: AamException) {
            return handleException(ex)
        }

        return ResponseEntity.ok(allReports.map { report ->
            ReportDto(
                id = report.id,
                name = report.title,
                schema = null,
            )
        })
    }

    @GetMapping("/{reportId}")
    fun fetchReport(
        @PathVariable reportId: String
    ): ResponseEntity<Any> {
        val report = try {
            reportStorage.fetchReport(DomainReference(id = reportId))
        } catch (ex: AamException) {
            return handleException(ex)
        }

        return ResponseEntity.ok(
            ReportDto(
                id = report.id,
                name = report.title,
                schema = null,
            )
        )
    }

    private fun handleException(ex: AamException): ResponseEntity<Any> {
        return when (ex) {
            is NotFoundException -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                    HttpErrorDto(
                        errorCode = "NOT_FOUND",
                        errorMessage = ex.localizedMessage,
                    )
                )

            is NetworkException -> ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(
                    HttpErrorDto(
                        errorCode = "GATEWAY_TIMEOUT",
                        errorMessage = ex.localizedMessage,
                    )
                )

            is InvalidArgumentException -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                    HttpErrorDto(
                        errorCode = "BAD_REQUEST",
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
}
