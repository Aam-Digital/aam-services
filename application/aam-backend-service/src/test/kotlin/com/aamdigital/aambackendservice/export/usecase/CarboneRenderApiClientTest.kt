package com.aamdigital.aambackendservice.export.usecase

import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.web.client.RestClient

class CarboneRenderApiClientTest {
    private enum class TestErrorCode : AamErrorCode { PARSE_ERROR }

    private val client =
        CarboneRenderApiClient(
            renderClient = RestClient.create(),
            objectMapper = jacksonObjectMapper(),
            templateStorage = mock(),
            notFoundCode = TestErrorCode.PARSE_ERROR,
            fetchTemplateFailedCode = TestErrorCode.PARSE_ERROR,
            createRenderRequestFailedCode = TestErrorCode.PARSE_ERROR,
            fetchRenderResultFailedCode = TestErrorCode.PARSE_ERROR,
            parseResponseCode = TestErrorCode.PARSE_ERROR
        )

    @Test
    fun `parseRenderId returns the renderId from a valid success response`() {
        val raw = """{"success":true,"data":{"renderId":"render-123"}}"""

        assertThat(client.parseRenderId(raw)).isEqualTo("render-123")
    }

    @Test
    fun `parseRenderId surfaces the carbone error message from a structured error response`() {
        val raw = """{"success":false,"error":"template invalid"}"""

        assertThatThrownBy { client.parseRenderId(raw) }
            .isInstanceOf(ExternalSystemException::class.java)
            .hasMessageContaining("template invalid")
    }

    @Test
    fun `parseRenderId surfaces the raw response when it matches no known shape`() {
        val raw = "totally-unexpected-body"

        assertThatThrownBy { client.parseRenderId(raw) }
            .isInstanceOf(ExternalSystemException::class.java)
            .hasMessageContaining("totally-unexpected-body")
    }
}
