package com.aamdigital.aambackendservice.reporting.reportcalculation.controller

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.FileStorage
import com.aamdigital.aambackendservice.common.error.HttpErrorDto
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.export.controller.TemplateExportControllerResponse
import com.aamdigital.aambackendservice.reporting.ConditionalOnReportingEnabled
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculation
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationResult
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationStorage
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
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
import java.io.InputStream
import java.io.SequenceInputStream
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

@RestController
@RequestMapping("/v1/reporting/report-calculation")
@ConditionalOnReportingEnabled
@Validated
class ReportCalculationController(
    private val reportStorage: ReportStorage,
    private val reportCalculationStorage: ReportCalculationStorage,
    private val fileStorage: FileStorage,
    private val createReportCalculationUseCase: CreateReportCalculationUseCase,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/report/{reportId}")
    fun startCalculation(
        @PathVariable reportId: String,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") from: Date?,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") to: Date?
    ): ResponseEntity<Any> {
        val report =
            try {
                reportStorage.fetchReport(DomainReference(id = reportId))
            } catch (ex: NotFoundException) {
                return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(
                        HttpErrorDto(
                            errorCode = "NOT_FOUND",
                            errorMessage = "Could not find report with id $reportId"
                        )
                    )
            }

        val args = mutableMapOf<String, String>()

        if (from != null) {
            args["startDate"] = from.toInstant().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME)
        }

        if (to != null) {
            args["endDate"] = to.toInstant().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME)
        }

        val createReportCalculationResponse =
            createReportCalculationUseCase.createReportCalculation(
                CreateReportCalculationRequest(
                    report = DomainReference(report.id),
                    args = args
                )
            )

        logger.trace(
            "[POST /report/{reportId}]: Returning response for {}: {}",
            report.id,
            createReportCalculationResponse
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

        logger.trace("[GET /report/{reportId}]: Returning $reportId")
        return reportCalculations.map { toDto(it) }
    }

    @GetMapping("/{calculationId}")
    fun fetchReportCalculation(
        @PathVariable calculationId: String
    ): ResponseEntity<Any> {
        val reportCalculation =
            try {
                reportCalculationStorage.fetchReportCalculation(DomainReference(id = calculationId))
            } catch (ex: NotFoundException) {
                logger.trace("[GET /{calculationId}]: Requested calculationId $calculationId not found")
                return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(
                        HttpErrorDto(
                            errorCode = "NOT_FOUND",
                            errorMessage = "Could not find reportCalculation with id $calculationId"
                        )
                    )
            }

        // TODO Auth check (https://github.com/Aam-Digital/aam-services/issues/10)

        logger.trace("[GET /{calculationId}]: Returning $calculationId for ${reportCalculation.report.id}")
        return ResponseEntity.ok(toDto(reportCalculation))
    }

    /**
     * Streams the calculated data, wrapped in a small envelope of metadata.
     *
     * The body is an [InputStreamResource] and *not* a
     * `org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody` on purpose.
     * A StreamingResponseBody is written from an async task while the container owns the request
     * lifecycle, so Tomcat could recycle the request underneath the writer - on the async timeout
     * (30s by default, which by itself truncates large downloads) or when the client goes away.
     * Both threads then touch the same `MimeHeaders`, which is not thread safe, and it fails with
     * a NullPointerException from inside Tomcat while committing the response, sometimes taking
     * whichever request next reuses the recycled objects with it. Neither Tomcat nor Spring treats
     * that as fixable on their side (spring-framework#33439 was closed as not planned).
     *
     * Returning a Resource keeps the copy on the request thread, where the container cannot
     * recycle anything until the handler returns. Virtual threads are enabled, so blocking that
     * thread for the length of a download is cheap, and a client that disappears mid-download
     * surfaces as a `ClientAbortException` that Spring's `DefaultHandlerExceptionResolver` logs at
     * DEBUG. `ResourceHttpMessageConverter` copies the stream verbatim and closes it afterwards.
     */
    @GetMapping("/{calculationId}/data", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun fetchReportCalculationData(
        @PathVariable("calculationId") calculationIdRaw: String
    ): ResponseEntity<InputStreamResource> {
        // TODO Auth check (https://github.com/Aam-Digital/aam-services/issues/10)

        if (calculationIdRaw.isBlank() || calculationIdRaw.trim().isEmpty()) {
            logger.debug("[GET /{calculationId}/data]: Invalid calculationId $calculationIdRaw")
            return ResponseEntity(
                getErrorBody(errorCode = "INVALID_DATA", "Invalid calculationId."),
                HttpHeaders().apply {
                    set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                },
                HttpStatus.NOT_FOUND
            )
        }

        val calculationId = calculationIdRaw.trim()

        val file: InputStream =
            try {
                fileStorage.fetchFile(
                    path = "report-calculation/$calculationId",
                    fileName = "data.json"
                )
            } catch (ex: NotFoundException) {
                logger.trace("[GET /{calculationId}/data]: Requested calculationId $calculationId file not found")
                return ResponseEntity(
                    getErrorBody(errorCode = ex.code.toString(), ex.localizedMessage),
                    HttpHeaders().apply {
                        set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    },
                    HttpStatus.NOT_FOUND
                )
            }

        val reportCalculation =
            try {
                reportCalculationStorage.fetchReportCalculation(DomainReference(id = calculationId))
            } catch (ex: NotFoundException) {
                logger.trace("[GET /{calculationId}/data]: Requested calculationId $calculationId not found")
                // nothing will consume the attachment stream that was opened above
                file.close()
                return ResponseEntity(
                    getErrorBody(errorCode = ex.code.toString(), ex.localizedMessage),
                    HttpHeaders().apply {
                        set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    },
                    HttpStatus.NOT_FOUND
                )
            }

        val responseBody =
            InputStreamResource(
                SequenceInputStream(
                    Collections.enumeration(
                        listOf(
                            (
                                "{\"id\": \"${calculationId}_data.json\"," +
                                    "\"report\": {\"id\": \"${reportCalculation.report.id}\"}," +
                                    "\"calculation\":{\"id\": \"$calculationId\"}," +
                                    "\"dataHash\": \"${reportCalculation.attachments["data.json"]?.digest}\"," +
                                    "\"data\":"
                            ).byteInputStream(),
                            file,
                            "}".byteInputStream()
                        )
                    )
                )
            )

        logger.trace(
            "[GET /{calculationId}/data]: Returning stream for ${reportCalculation.report.id} calculationId $calculationId with ${reportCalculation.attachments["data.json"]?.digest}"
        )
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$calculationId-data.json")
            .contentType(MediaType.APPLICATION_JSON)
            .body(responseBody)
    }

    /**
     * Streams the calculated data as it is stored, without the metadata envelope.
     *
     * Returns an [InputStreamResource] rather than a StreamingResponseBody for the reasons given
     * on [fetchReportCalculationData].
     */
    @GetMapping("/{calculationId}/data-stream", produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun fetchReportCalculationDataStream(
        @PathVariable calculationId: String
    ): ResponseEntity<InputStreamResource> {
        val file =
            try {
                fileStorage.fetchFile(
                    path = "report-calculation/$calculationId",
                    fileName = "data.json"
                )
            } catch (ex: NotFoundException) {
                logger.trace(
                    "[GET /{calculationId}/data-stream]: Requested calculationId $calculationId file not found"
                )
                return ResponseEntity(
                    getErrorBody(errorCode = ex.code.toString(), ex.localizedMessage),
                    HttpHeaders().apply {
                        set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    },
                    HttpStatus.NOT_FOUND
                )
            }

        val reportCalculation =
            try {
                reportCalculationStorage.fetchReportCalculation(DomainReference(id = calculationId))
            } catch (ex: NotFoundException) {
                logger.trace("[GET /{calculationId}/data-stream]: Requested calculationId $calculationId not found")
                // nothing will consume the attachment stream that was opened above
                file.close()
                return ResponseEntity(
                    getErrorBody(errorCode = ex.code.toString(), ex.localizedMessage),
                    HttpHeaders().apply {
                        set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    },
                    HttpStatus.NOT_FOUND
                )
            }

        val responseBody = InputStreamResource(file)

        logger.trace(
            "[GET /{calculationId}/data-stream]: Returning stream for ${reportCalculation.report.id} calculationId $calculationId"
        )
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=calculation-data.json")
            .contentType(MediaType.APPLICATION_JSON)
            .body(responseBody)
    }

    /*
     * Keeps the error responses the same body type as the success ones, so no converter is needed.
     */
    private fun getErrorBody(
        errorCode: String,
        errorMessage: String
    ) = InputStreamResource(
        objectMapper
            .writeValueAsString(
                TemplateExportControllerResponse.ErrorControllerResponse(
                    errorCode = errorCode,
                    errorMessage = errorMessage
                )
            ).byteInputStream()
    )

    private fun toDto(it: ReportCalculation): ReportCalculationDto {
        val result =
            ReportCalculationDto(
                id = it.id,
                report = it.report,
                status = it.status,
                startDate = it.calculationStarted,
                endDate = it.calculationCompleted,
                args = it.args,
                data = toReportCalculationData(it)
            )

        if (it.status == ReportCalculationStatus.FINISHED_ERROR) {
            result.errorDetails = toErrorDetails(it.errorDetails)
        }

        return result
    }

    private fun toErrorDetails(it: String?): String {
        // e.g. "400 Bad Request: \"{\"statusCode\":400,\"error\":\"Bad Request\",\"message\":\"no such column: i.xxx\"}\""
        // should be returned as "no such column: i.xxx"

        if (it.isNullOrBlank()) {
            return "Unknown error"
        }

        return Regex("""message":"(.*?)"""").find(it)?.groupValues?.getOrNull(1) ?: "Unknown error"
    }

    private fun toReportCalculationData(it: ReportCalculation): ReportCalculationData? {
        val attachment = it.attachments["data.json"] ?: return null
        return ReportCalculationData(
            contentType = attachment.contentType,
            hash = attachment.digest,
            length = attachment.length
        )
    }
}
