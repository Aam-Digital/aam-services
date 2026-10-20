package com.aamdigital.aambackendservice.reporting.report.storage

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ReportTitleTest {
    private val objectMapper = ObjectMapper()

    @ParameterizedTest(name = "{0} -> \"{1}\"")
    @MethodSource("titleCases")
    fun `should resolve a plain or multi-lingual title to one display text`(
        titleJson: String,
        expected: String
    ) {
        assertThat(resolveReportTitle(objectMapper.readTree(titleJson))).isEqualTo(expected)
    }

    @Test
    fun `should resolve a missing title to an empty text`() {
        assertThat(resolveReportTitle(null)).isEqualTo("")
    }

    companion object {
        @JvmStatic
        fun titleCases(): List<Arguments> =
            listOf(
                Arguments.of("\"Basic Report\"", "Basic Report"),
                Arguments.of("""{"de": "Basis-Bericht", "en-US": "Basic Report"}""", "Basic Report"),
                Arguments.of("""{"de": "Basis-Bericht", "en-US": " ", "en_GB": "Basic report"}""", "Basic report"),
                Arguments.of("""{"fr": "", "de": "Basis-Bericht", "es": "Informe"}""", "Basis-Bericht"),
                Arguments.of("""{"en-US": 42, "de": "Basis-Bericht"}""", "Basis-Bericht"),
                Arguments.of("{}", ""),
                Arguments.of("null", ""),
                Arguments.of("42", "")
            )
    }
}
