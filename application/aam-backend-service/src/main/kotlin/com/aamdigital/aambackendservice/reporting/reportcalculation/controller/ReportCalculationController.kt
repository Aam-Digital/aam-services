package com.aamdigital.aambackendservice.reporting.reportcalculation.controller

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.HttpErrorDto
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.export.controller.TemplateExportControllerResponse
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.report.core.ReportingStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationResult
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.dto.ReportCalculationData
import com.aamdigital.aambackendservice.reporting.reportcalculation.dto.ReportCalculationDto
import com.aamdigital.aambackendservice.reporting.storage.DefaultReportStorage
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.OutputStream
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*


@RestController
@RequestMapping("/v1/reporting/report-calculation")
@Validated
class ReportCalculationController(
    private val reportingStorage: ReportingStorage,
    private val reportStorage: DefaultReportStorage,
    private val createReportCalculationUseCase: CreateReportCalculationUseCase,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val BYTE_ARRAY_BUFFER_LENGTH = 4096
    }

    /*
     * Needed so be able to return "ResponseEntity<StreamingResponseBody>" without the need to write a converter.
     */
    private fun getErrorStreamingBody(errorCode: String, errorMessage: String) =
        StreamingResponseBody { outputStream: OutputStream ->
            val buffer = ByteArray(BYTE_ARRAY_BUFFER_LENGTH)
            var bytesRead: Int

            val bodyStream = objectMapper.writeValueAsString(
                TemplateExportControllerResponse.ErrorControllerResponse(
                    errorCode = errorCode,
                    errorMessage = errorMessage,
                )
            ).byteInputStream()

            while ((bodyStream.read(buffer).also { bytesRead = it }) != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
        }

    @PostMapping("/report/{reportId}")
    fun startCalculation(
        @PathVariable reportId: String,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") from: Date?,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") to: Date?,
    ): ResponseEntity<Any> {
        val reportOptional = reportStorage.fetchReport(DomainReference(id = reportId))

        if (reportOptional.isEmpty) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                    HttpErrorDto(
                        errorCode = "NOT_FOUND",
                        errorMessage = "Could not find report with id $reportId"
                    )
                )
        }

        val report = reportOptional.get()

        val args = mutableMapOf<String, String>()

        if (from != null) {
            args["from"] = from.toInstant().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME)
        }

        if (to != null) {
            args["to"] = to.toInstant().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME)
        }

        val result = createReportCalculationUseCase.createReportCalculation(
            CreateReportCalculationRequest(
                report = DomainReference(report.id), args = args
            )
        )

        return when (result) {
            is CreateReportCalculationResult.Failure -> {
                return ResponseEntity.internalServerError().build()
            }

            is CreateReportCalculationResult.Success -> ResponseEntity.ok(result.calculation)
        }
    }

    @GetMapping("/report/{reportId}")
    fun fetchReportCalculations(
        @PathVariable reportId: String
    ): List<ReportCalculationDto> {
        val reportCalculations = reportingStorage.fetchCalculations(DomainReference(id = reportId))

        return reportCalculations.map { toDto(it) }
    }

    @GetMapping("/{calculationId}")
    fun fetchReportCalculation(
        @PathVariable calculationId: String
    ): ResponseEntity<Any> {

        val reportCalculationOptional = reportingStorage.fetchCalculation(DomainReference(id = calculationId))

        if (reportCalculationOptional.isEmpty) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                    HttpErrorDto(
                        errorCode = "NOT_FOUND",
                        errorMessage = "Could not find reportCalculation with id $calculationId"
                    )
                )
        }

        val reportCalculation = reportCalculationOptional.get()

        // TODO Auth check (https://github.com/Aam-Digital/aam-services/issues/10)

        return ResponseEntity.ok(toDto(reportCalculation))
    }

    @GetMapping("/{calculationId}/data", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun fetchReportCalculationData(
        @PathVariable calculationId: String
    ): ResponseEntity<StreamingResponseBody> {
        // TODO Auth check (https://github.com/Aam-Digital/aam-services/issues/10)

        val headData = reportingStorage.headData(DomainReference(calculationId))

        if (headData.eTag.isNullOrBlank()) {
            val errorStreamingBody =
                getErrorStreamingBody(errorCode = "NOT_FOUND", "Could not fetch data for $calculationId")
            val headers = HttpHeaders()
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)

            return ResponseEntity(
                errorStreamingBody,
                headers,
                HttpStatus.NOT_FOUND,
            )
        }

        val calculationOptional = reportingStorage.fetchCalculation(DomainReference(calculationId))

        if (calculationOptional.isEmpty) {
            val errorStreamingBody =
                getErrorStreamingBody(errorCode = "NOT_FOUND", "Could not fetch calculation: $calculationId")
            val headers = HttpHeaders()
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)

            return ResponseEntity(
                errorStreamingBody,
                headers,
                HttpStatus.NOT_FOUND,
            )
        }

        val calculation = calculationOptional.get()

        val fileStream = reportingStorage
            .fetchData(DomainReference(id = calculationId))

        val prefix = """
            { 
                "id": "${calculationId}_data.json",
                "report": {
                    "id": "${calculation.report.id}"
                },
                "calculation": {
                    "id": "$calculationId"
                },
                "dataHash": "${calculation.attachments["data.json"]?.digest}",
                "data": 
        """.trimIndent().toByteArray()

        val suffix = """
            } 
        """.trimIndent().toByteArray()

        val responseBody = StreamingResponseBody { outputStream: OutputStream ->
            outputStream.write(prefix.inputStream().readBytes())

            val buffer = ByteArray(BYTE_ARRAY_BUFFER_LENGTH)
            var bytesRead: Int
            while ((fileStream.read(buffer).also { bytesRead = it }) != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            outputStream.write(suffix.inputStream().readBytes())
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=calculation-data.json")
            .contentType(MediaType.APPLICATION_JSON)
            .body(responseBody)
    }

    @GetMapping("/{calculationId}/data-stream", produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun fetchReportCalculationDataStream(
        @PathVariable calculationId: String
    ): ResponseEntity<StreamingResponseBody> {
        val fileStream = try {
            reportingStorage
                .fetchData(DomainReference(id = calculationId))
        } catch (ex: NotFoundException) {
            val errorStreamingBody =
                getErrorStreamingBody(errorCode = ex.code.toString(), ex.localizedMessage)
            val headers = HttpHeaders()
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)

            return ResponseEntity(
                errorStreamingBody,
                headers,
                HttpStatus.NOT_FOUND,
            )
        }

        val responseBody = StreamingResponseBody { outputStream: OutputStream ->
            val buffer = ByteArray(BYTE_ARRAY_BUFFER_LENGTH)
            var bytesRead: Int
            while ((fileStream.read(buffer).also { bytesRead = it }) != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=calculation-data.json")
            .contentType(MediaType.APPLICATION_JSON)
            .body(responseBody)
    }

    private fun toDto(it: ReportCalculation): ReportCalculationDto = ReportCalculationDto(
        id = it.id,
        report = it.report,
        status = it.status,
        startDate = it.calculationStarted,
        endDate = it.calculationCompleted,
        args = it.args,
        data = toReportCalculationData(it),
    )

    private fun toReportCalculationData(it: ReportCalculation): ReportCalculationData? {
        val attachment = it.attachments["data.json"] ?: return null
        return ReportCalculationData(
            contentType = attachment.contentType,
            hash = attachment.digest,
            length = attachment.length
        )
    }
}
