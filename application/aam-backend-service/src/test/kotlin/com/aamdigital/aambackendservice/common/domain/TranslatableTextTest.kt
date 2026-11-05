package com.aamdigital.aambackendservice.common.domain

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TranslatableTextTest {
    private val objectMapper = jacksonObjectMapper()

    data class TestDto(
        @JsonDeserialize(using = TranslatableTextDeserializer::class)
        val title: String,
        @JsonDeserialize(using = TranslatableTextDeserializer::class)
        val description: String? = null
    )

    private fun resolve(json: String): String? = TranslatableText.resolve(objectMapper.readTree(json))

    @Test
    fun `should keep a plain string`() {
        assertThat(resolve(""""Report"""")).isEqualTo("Report")
    }

    @Test
    fun `should pick the English translation`() {
        assertThat(resolve("""{ "de": "Bericht", "en-US": "Report" }""")).isEqualTo("Report")
        assertThat(resolve("""{ "de": "Bericht", "en": "Report" }""")).isEqualTo("Report")
    }

    @Test
    fun `should fall back to any non-empty translation without English`() {
        assertThat(resolve("""{ "en-US": "", "de": "Bericht" }""")).isEqualTo("Bericht")
    }

    @Test
    fun `should resolve values without text to null`() {
        assertThat(TranslatableText.resolve(null)).isNull()
        assertThat(resolve("null")).isNull()
        assertThat(resolve("{}")).isNull()
        assertThat(resolve("42")).isNull()
    }

    @Test
    fun `should deserialize plain and multi-lingual texts into strings`() {
        val dto =
            objectMapper.readValue<TestDto>(
                """{ "title": { "en-US": "Report", "de": "Bericht" }, "description": "plain" }"""
            )

        assertThat(dto).isEqualTo(TestDto(title = "Report", description = "plain"))
    }

    @Test
    fun `should deserialize a missing or null optional text to null`() {
        assertThat(objectMapper.readValue<TestDto>("""{ "title": "Report" }""").description).isNull()
        assertThat(objectMapper.readValue<TestDto>("""{ "title": "Report", "description": null }""").description)
            .isNull()
    }
}
