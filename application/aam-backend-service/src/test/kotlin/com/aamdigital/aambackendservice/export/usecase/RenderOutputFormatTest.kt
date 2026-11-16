package com.aamdigital.aambackendservice.export.usecase

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class RenderOutputFormatTest {
    private val objectMapper = ObjectMapper()

    @ParameterizedTest
    @CsvSource(
        delimiter = ';',
        value = [
            """{"convertTo":"pdf"}; pdf""",
            """{"convertTo":"docx"}; docx""",
            """{"convertTo":"DOCX"}; docx""",
            """{"convertTo":{"formatName":"xlsx","formatOptions":{}}}; xlsx""",
            """{"data":{}}; pdf""",
            """{"convertTo":null}; pdf"""
        ]
    )
    fun `should read the requested format from convertTo and default to pdf`(
        body: String,
        expectedFormat: String
    ) {
        assertThat(RenderOutputFormat.fromRequestBody(objectMapper.readTree(body))).isEqualTo(expectedFormat)
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = ';',
        value = [
            """{"convertTo":"../../etc/passwd"}""",
            """{"convertTo":"pdf\" filename=\"x"}""",
            """{"convertTo":""}""",
            """{"convertTo":42}""",
            """{"convertTo":{"formatOptions":{}}}"""
        ]
    )
    fun `should reject a convertTo that is not a plain file extension`(body: String) {
        assertThat(RenderOutputFormat.fromRequestBody(objectMapper.readTree(body))).isNull()
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            """{"data":{}} | {"data":{},"convertTo":"pdf"}""",
            """{"convertTo":"DOCX"} | {"convertTo":"docx"}""",
            """{"convertTo":{"formatName":"XLSX","formatOptions":{"a":1}}} | {"convertTo":{"formatName":"xlsx","formatOptions":{"a":1}}}"""
        ]
    )
    fun `should send the template engine the exact format the file is named after`(
        body: String,
        expectedBody: String
    ) {
        val bodyData = objectMapper.readTree(body) as ObjectNode

        RenderOutputFormat.applyTo(bodyData, RenderOutputFormat.fromRequestBody(bodyData)!!)

        assertThat(bodyData).isEqualTo(objectMapper.readTree(expectedBody))
    }

    @ParameterizedTest
    @CsvSource(
        value = [
            "report_{d.name}.pdf, pdf, report_{d.name}.pdf",
            "report_{d.name}.PDF, pdf, report_{d.name}.pdf",
            "report_{d.name}, pdf, report_{d.name}.pdf",
            "report_{d.name}.pdf, docx, report_{d.name}.docx",
            "report_{d.name}.docx, docx, report_{d.name}.docx",
            "report_{d.name}.docx, pdf, report_{d.name}.pdf",
            "report_{d.name}, xlsx, report_{d.name}.xlsx",
            "report_v1.2_{d.name}, docx, report_v1.2_{d.name}.docx",
            "bad/path*name.pdf, docx, bad_path_name.docx"
        ]
    )
    fun `should end the target file name in the extension of the generated format`(
        fileNamePattern: String,
        format: String,
        expectedFileName: String
    ) {
        assertThat(RenderOutputFormat.targetFileName(fileNamePattern, format)).isEqualTo(expectedFileName)
    }
}
