package com.aamdigital.aambackendservice.reporting.reportcalculation.controller

import com.aamdigital.aambackendservice.common.couchdb.dto.AttachmentMetaData
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.FileStorage
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculation
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.CreateReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.ReportCalculationStorage
import com.aamdigital.aambackendservice.reporting.reportcalculation.storage.DefaultReportCalculationStorage.DefaultReportCalculationStorageError
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.ByteArrayInputStream
import java.io.InputStream

@ExtendWith(MockitoExtension::class)
class ReportCalculationControllerTest {
    @Mock
    private lateinit var reportStorage: ReportStorage

    @Mock
    private lateinit var reportCalculationStorage: ReportCalculationStorage

    @Mock
    private lateinit var fileStorage: FileStorage

    @Mock
    private lateinit var createReportCalculationUseCase: CreateReportCalculationUseCase

    private lateinit var mockMvc: MockMvc

    companion object {
        private const val CALCULATION_ID = "ReportCalculation:1"
        private const val DATA = """[{"name":"Bärbel"}]"""
    }

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(
                    ReportCalculationController(
                        reportStorage = reportStorage,
                        reportCalculationStorage = reportCalculationStorage,
                        fileStorage = fileStorage,
                        createReportCalculationUseCase = createReportCalculationUseCase,
                        objectMapper = ObjectMapper()
                    )
                ).build()
    }

    private fun storedCalculation(): ReportCalculation =
        ReportCalculation(
            id = CALCULATION_ID,
            report = DomainReference(id = "ReportConfig:1"),
            status = ReportCalculationStatus.FINISHED_SUCCESS,
            attachments =
                mutableMapOf(
                    "data.json" to
                        AttachmentMetaData(
                            contentType = "application/json",
                            revpos = 2,
                            digest = "md5-someDigest",
                            length = DATA.length.toLong(),
                            stub = true
                        )
                )
        )

    @Test
    fun `writes the data on the request thread instead of starting an async response`() {
        // Given - a StreamingResponseBody would hand the write to an async task, which lets the
        // container recycle the request underneath the writer
        whenever(fileStorage.fetchFile(path = "report-calculation/$CALCULATION_ID", fileName = "data.json"))
            .thenReturn(DATA.byteInputStream())
        whenever(reportCalculationStorage.fetchReportCalculation(DomainReference(id = CALCULATION_ID)))
            .thenReturn(storedCalculation())

        // When
        val result = mockMvc.perform(get("/v1/reporting/report-calculation/$CALCULATION_ID/data"))

        // Then
        result
            .andExpect(status().isOk)
            .andExpect(request().asyncNotStarted())
    }

    @Test
    fun `returns the stored data wrapped in the metadata envelope, unaltered and without a content length`() {
        // Given
        whenever(fileStorage.fetchFile(path = "report-calculation/$CALCULATION_ID", fileName = "data.json"))
            .thenReturn(DATA.byteInputStream())
        whenever(reportCalculationStorage.fetchReportCalculation(DomainReference(id = CALCULATION_ID)))
            .thenReturn(storedCalculation())

        // When
        val response =
            mockMvc
                .perform(get("/v1/reporting/report-calculation/$CALCULATION_ID/data"))
                .andReturn()
                .response

        // Then
        assertThat(response.contentAsString).isEqualTo(
            """{"id": "${CALCULATION_ID}_data.json",""" +
                """"report": {"id": "ReportConfig:1"},""" +
                """"calculation":{"id": "$CALCULATION_ID"},""" +
                """"dataHash": "md5-someDigest",""" +
                """"data":$DATA}"""
        )
        // A content length would mean the resource was drained to measure it, leaving an empty body
        assertThat(response.getHeader(HttpHeaders.CONTENT_LENGTH)).isNull()
        assertThat(response.getHeader(HttpHeaders.CONTENT_DISPOSITION))
            .isEqualTo("attachment; filename=$CALCULATION_ID-data.json")
    }

    @Test
    fun `returns the stored data as-is on the data-stream endpoint`() {
        // Given
        whenever(fileStorage.fetchFile(path = "report-calculation/$CALCULATION_ID", fileName = "data.json"))
            .thenReturn(DATA.byteInputStream())
        whenever(reportCalculationStorage.fetchReportCalculation(DomainReference(id = CALCULATION_ID)))
            .thenReturn(storedCalculation())

        // When
        val response =
            mockMvc
                .perform(get("/v1/reporting/report-calculation/$CALCULATION_ID/data-stream"))
                .andReturn()
                .response

        // Then
        assertThat(response.status).isEqualTo(200)
        assertThat(response.contentAsString).isEqualTo(DATA)
    }

    @Test
    fun `answers with a json error body when the calculation is unknown`() {
        // Given
        whenever(fileStorage.fetchFile(path = "report-calculation/$CALCULATION_ID", fileName = "data.json"))
            .thenReturn(DATA.byteInputStream())
        whenever(reportCalculationStorage.fetchReportCalculation(DomainReference(id = CALCULATION_ID)))
            .thenThrow(
                NotFoundException(
                    message = "Could not find reportCalculation",
                    code = DefaultReportCalculationStorageError.NOT_FOUND
                )
            )

        // When
        val response =
            mockMvc
                .perform(get("/v1/reporting/report-calculation/$CALCULATION_ID/data"))
                .andReturn()
                .response

        // Then
        assertThat(response.status).isEqualTo(404)
        assertThat(response.contentAsString).contains("NOT_FOUND", "Could not find reportCalculation")
    }

    @Test
    fun `closes the attachment stream when the calculation lookup fails afterwards`() {
        // Given - the stream is opened before the lookup, so nothing would ever consume or close it
        val attachment = CloseRecordingInputStream(DATA.byteInputStream())
        whenever(fileStorage.fetchFile(path = "report-calculation/$CALCULATION_ID", fileName = "data.json"))
            .thenReturn(attachment)
        whenever(reportCalculationStorage.fetchReportCalculation(DomainReference(id = CALCULATION_ID)))
            .thenThrow(
                NotFoundException(
                    message = "Could not find reportCalculation",
                    code = DefaultReportCalculationStorageError.NOT_FOUND
                )
            )

        // When
        mockMvc.perform(get("/v1/reporting/report-calculation/$CALCULATION_ID/data"))

        // Then
        assertThat(attachment.closed).isTrue()
    }

    private class CloseRecordingInputStream(
        private val delegate: ByteArrayInputStream
    ) : InputStream() {
        var closed = false
            private set

        override fun read(): Int = delegate.read()

        override fun close() {
            closed = true
            delegate.close()
        }
    }
}
