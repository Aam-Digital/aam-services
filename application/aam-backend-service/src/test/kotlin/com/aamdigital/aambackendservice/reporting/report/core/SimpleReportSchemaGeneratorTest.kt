package com.aamdigital.aambackendservice.reporting.report.core

import jakarta.json.JsonObject
import net.joshka.junit.json.params.JsonFileSource
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.mockito.junit.jupiter.MockitoExtension


@ExtendWith(MockitoExtension::class)
internal class SimpleReportSchemaGeneratorTest {

    private lateinit var simpleReportSchemaGenerator: SimpleReportSchemaGenerator

    @BeforeEach
    fun setUp() {
        simpleReportSchemaGenerator = SimpleReportSchemaGenerator()
    }

    @ParameterizedTest
    @JsonFileSource(resources = ["/reporting/report/core/get-table-names-by-query.json"])
    fun `getTableNamesByQuery() should return all entities in query`(data: JsonObject) {
        // given
        val query = data.getString("query").replace("\"", "")
        val expectedFields = data.getJsonArray("fields").map { it.toString().replace("\"", "") }

        // when
        val result = simpleReportSchemaGenerator.getTableNamesByQuery(query)

        // then
        Assertions.assertEquals(expectedFields, result)
    }
}
