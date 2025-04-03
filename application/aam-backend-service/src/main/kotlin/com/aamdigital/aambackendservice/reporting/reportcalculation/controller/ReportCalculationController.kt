package com.aamdigital.aambackendservice.reporting.reportcalculation.controller

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.domain.FileStorage
import com.aamdigital.aambackendservice.error.HttpErrorDto
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.export.controller.TemplateExportControllerResponse
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationResult
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.dto.ReportCalculationData
import com.aamdigital.aambackendservice.reporting.reportcalculation.dto.ReportCalculationDto
import com.aamdigital.aambackendservice.stream.handleInputStreamToOutputStream
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
import java.io.InputStream
import java.io.OutputStream
import java.io.SequenceInputStream
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*


@RestController
@RequestMapping("/v1/reporting/report-calculation")
@Validated
class ReportCalculationController(
    private val reportStorage: ReportStorage,
    private val reportCalculationStorage: ReportCalculationStorage,
    private val fileStorage: FileStorage,
    private val createReportCalculationUseCase: CreateReportCalculationUseCase,
    private val objectMapper: ObjectMapper,
) {

    @PostMapping("/report/{reportId}")
    fun startCalculation(
        @PathVariable reportId: String,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") from: Date?,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") to: Date?,
    ): ResponseEntity<Any> {
        val report = try {
            reportStorage.fetchReport(DomainReference(id = reportId))
        } catch (ex: NotFoundException) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                    HttpErrorDto(
                        errorCode = "NOT_FOUND",
                        errorMessage = "Could not find report with id $reportId"
                    )
                )
        }

        val args = mutableMapOf<String, String>()

        if (from != null) {
            if (report.version == 1) {
                args["from"] = from.toInstant().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME)
            } else {
                args["startDate"] = from.toInstant().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME)
            }
        }

        if (to != null) {
            if (report.version == 1) {
                args["to"] = to.toInstant().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME)
            } else {
                args["endDate"] = to.toInstant().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME)
            }
        }

        val createReportCalculationResponse = createReportCalculationUseCase.createReportCalculation(
            CreateReportCalculationRequest(
                report = DomainReference(report.id), args = args
            )
        )

        return when (createReportCalculationResponse) {
            is CreateReportCalculationResult.Failure -> {
                return ResponseEntity.internalServerError().build()
            }

            is CreateReportCalculationResult.Success -> {
                ResponseEntity.ok(createReportCalculationResponse.calculation)
            }
        }
    }

    @GetMapping("/report/{reportId}")
    fun fetchReportCalculations(
        @PathVariable reportId: String
    ): List<ReportCalculationDto> {
        val reportCalculations = reportCalculationStorage.fetchReportCalculations(DomainReference(id = reportId))

        return reportCalculations.map { toDto(it) }
    }

    @GetMapping("/{calculationId}")
    fun fetchReportCalculation(
        @PathVariable calculationId: String
    ): ResponseEntity<Any> {
        val reportCalculation = try {
            reportCalculationStorage.fetchReportCalculation(DomainReference(id = calculationId))
        } catch (ex: NotFoundException) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                    HttpErrorDto(
                        errorCode = "NOT_FOUND",
                        errorMessage = "Could not find reportCalculation with id $calculationId"
                    )
                )
        }

        // TODO Auth check (https://github.com/Aam-Digital/aam-services/issues/10)

        return ResponseEntity.ok(toDto(reportCalculation))
    }

    @GetMapping("/{calculationId}/data", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun fetchReportCalculationData(
        @PathVariable("calculationId") calculationIdRaw: String
    ): ResponseEntity<StreamingResponseBody> {
        // TODO Auth check (https://github.com/Aam-Digital/aam-services/issues/10)

        if (calculationIdRaw.isBlank() || calculationIdRaw.trim().isEmpty()) {
            return ResponseEntity(
                getErrorStreamingBody(errorCode = "INVALID_DATA", "Invalid calculationId."),
                HttpHeaders().apply {
                    set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                },
                HttpStatus.NOT_FOUND,
            )
        }

        val calculationId = calculationIdRaw.trim()

        val file: InputStream = try {
            fileStorage.fetchFile(
                path = "report-calculation/$calculationId",
                fileName = "data.json"
            )
        } catch (ex: NotFoundException) {
            return ResponseEntity(
                getErrorStreamingBody(errorCode = ex.code.toString(), ex.localizedMessage),
                HttpHeaders().apply {
                    set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                },
                HttpStatus.NOT_FOUND,
            )
        }

        val reportCalculation = try {
            reportCalculationStorage.fetchReportCalculation(DomainReference(id = calculationId))
        } catch (ex: NotFoundException) {
            return ResponseEntity(
                getErrorStreamingBody(errorCode = ex.code.toString(), ex.localizedMessage),
                HttpHeaders().apply {
                    set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                },
                HttpStatus.NOT_FOUND,
            )
        }

        val responseBody = StreamingResponseBody { outputStream: OutputStream ->
            handleInputStreamToOutputStream(
                outputStream, SequenceInputStream(
                    Collections.enumeration(
                        listOf(
                            ("{\"id\": \"${calculationId}_data.json\"," +
                                    "\"report\": {\"id\": \"${reportCalculation.report.id}\"}," +
                                    "\"calculation\":{\"id\": \"$calculationId\"}," +
                                    "\"dataHash\": \"${reportCalculation.attachments["data.json"]?.digest}\"," +
                                    "\"data\":")
                                .byteInputStream(),
                            file,
                            "}".byteInputStream()
                        )
                    )
                )
            )
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$calculationId-data.json")
            .contentType(MediaType.APPLICATION_JSON)
            .body(responseBody)
    }

    @GetMapping("/{calculationId}/data-stream", produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun fetchReportCalculationDataStream(
        @PathVariable calculationId: String
    ): ResponseEntity<StreamingResponseBody> {
        val file = try {
            fileStorage.fetchFile(
                path = "report-calculation/$calculationId",
                fileName = "data.json"
            )
        } catch (ex: NotFoundException) {
            return ResponseEntity(
                getErrorStreamingBody(errorCode = ex.code.toString(), ex.localizedMessage),
                HttpHeaders().apply {
                    set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                },
                HttpStatus.NOT_FOUND,
            )
        }

        val reportCalculation = try {
            reportCalculationStorage.fetchReportCalculation(DomainReference(id = calculationId))
        } catch (ex: NotFoundException) {
            return ResponseEntity(
                getErrorStreamingBody(errorCode = ex.code.toString(), ex.localizedMessage),
                HttpHeaders().apply {
                    set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                },
                HttpStatus.NOT_FOUND,
            )
        }

        val responseBody = StreamingResponseBody { outputStream: OutputStream ->
            handleInputStreamToOutputStream(outputStream, file)
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=calculation-data.json")
            .contentType(MediaType.APPLICATION_JSON)
            .body(responseBody)
    }

    /*
     * Needed to be able to return "ResponseEntity<StreamingResponseBody>" without the need to write a converter.
     */
    private fun getErrorStreamingBody(errorCode: String, errorMessage: String, byteArrayBufferLength: Int = 4096) =
        StreamingResponseBody { outputStream: OutputStream ->
            val buffer = ByteArray(byteArrayBufferLength)
            var bytesRead: Int

            val bodyStream = objectMapper.writeValueAsString(
                TemplateExportControllerResponse.ErrorControllerResponse(
                    errorCode = errorCode,
                    errorMessage = errorMessage,
                )
            ).byteInputStream()

            while ((bodyStream.read(buffer).also { bytesRead = it }) != -1) {
                if (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
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
