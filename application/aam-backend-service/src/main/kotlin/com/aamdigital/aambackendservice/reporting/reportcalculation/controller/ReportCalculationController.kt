package com.aamdigital.aambackendservice.reporting.reportcalculation.controller

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.InternalServerException
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.report.core.ReportingStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationResult
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.dto.ReportCalculationDto
import com.aamdigital.aambackendservice.reporting.storage.DefaultReportStorage
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
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
) {
    @PostMapping("/report/{reportId}")
    fun startCalculation(
        @PathVariable reportId: String,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") from: Date?,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") to: Date?,
    ): Mono<DomainReference> {
        return reportStorage.fetchReport(DomainReference(id = reportId)).flatMap { reportOptional ->
            val report = reportOptional.orElseThrow {
                NotFoundException()
            }

            val args = mutableMapOf<String, String>()

            if (from != null) {
                args["from"] = from.toInstant().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME)
            }

            if (to != null) {
                args["to"] = to.toInstant().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME)
            }

            createReportCalculationUseCase.createReportCalculation(
                CreateReportCalculationRequest(
                    report = DomainReference(report.id), args = args
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

    @GetMapping("/report/{reportId}")
    fun fetchReportCalculations(
        @PathVariable reportId: String
    ): Mono<List<ReportCalculationDto>> {
        return reportingStorage.fetchCalculations(DomainReference(id = reportId)).map { calculations ->
            calculations.map { toDto(it) }
        }
    }

    @GetMapping("/{calculationId}")
    fun fetchReportCalculation(
        @PathVariable calculationId: String
    ): Mono<ReportCalculationDto> {
        return reportingStorage.fetchCalculation(DomainReference(id = calculationId)).map { calculationOptional ->
            val calculation = calculationOptional.orElseThrow {
                NotFoundException()
            }

            // TODO Auth check

            toDto(calculation)
        }
    }

    @GetMapping("/{calculationId}/data", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun fetchReportCalculationData(
        @PathVariable calculationId: String
    ): Flux<DataBuffer> {
        // TODO Auth check (?)

        return reportingStorage.headData(DomainReference(calculationId))
            .toFlux()
            .flatMap {
                if (it.eTag.isNullOrBlank()) {
                    return@flatMap Flux.error { NotFoundException("No data available") }
                }

                val fileContent = reportingStorage
                    .fetchData(DomainReference(id = calculationId))

                val prefix = """
                    { 
                        "id": "${calculationId}_data.json",
                        "report": "removed",
                        "calculation": "$calculationId",
                        "data": 
                """.trimIndent().toByteArray()
                val prefixBuffer = DefaultDataBufferFactory().allocateBuffer(prefix.size)
                prefixBuffer.write(prefix)

                val suffix = """
                    } 
                """.trimIndent().toByteArray()
                val suffixBuffer = DefaultDataBufferFactory().allocateBuffer(suffix.size)
                suffixBuffer.write(suffix)

                return@flatMap Flux.concat(
                    Flux.just(prefixBuffer),
                    fileContent,
                    Flux.just(suffixBuffer),
                )
            }
    }

    @GetMapping("/{calculationId}/data-stream", produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun fetchReportCalculationDataStream(
        @PathVariable calculationId: String
    ): Flux<DataBuffer> {
        // TODO Auth check (?)
        return reportingStorage.fetchData(DomainReference(id = calculationId))
    }

    private fun toDto(it: ReportCalculation): ReportCalculationDto = ReportCalculationDto(
        id = it.id,
        report = it.report,
        status = it.status,
        startDate = it.startDate,
        endDate = it.endDate,
        args = it.args,
        attachments = it.attachments,
    )
}
