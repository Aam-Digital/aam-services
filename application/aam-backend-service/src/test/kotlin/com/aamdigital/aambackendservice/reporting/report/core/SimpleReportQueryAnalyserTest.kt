package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.reporting.report.Report
import com.aamdigital.aambackendservice.reporting.report.ReportItem
import jakarta.json.JsonObject
import net.joshka.junit.json.params.JsonFileSource
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class SimpleReportQueryAnalyserTest {
    private lateinit var service: SimpleReportQueryAnalyser

    @BeforeEach
    fun setUp() {
        service = SimpleReportQueryAnalyser()
    }

    @Test
    fun `should be able to handle empty items`() {
        // given
        val report = Report(
            id = "report-1",
            title = "Report 1",
            version = 1,
            transformations = emptyMap(),
            items = listOf()
        )

        // when
        val result = service.getAffectedEntities(report)

        // then
        Assertions.assertEquals(
            emptyList<String>(),
            result
        )
    }

    @Test
    fun `should be able to handle invalid SQL queries`() {
        // given
        val report = Report(
            id = "report-1",
            title = "Report 1",
            version = 1,
            transformations = emptyMap(),
            items = listOf(
                ReportItem.ReportQuery(
                    sql = "SELECT nothing"
                ),
                ReportItem.ReportQuery(
                    sql = "SELECT nothing FROM"
                ),
                ReportItem.ReportQuery(
                    sql = "FROM"
                )
            )
        )

        // when
        val result = service.getAffectedEntities(report)

        // then
        Assertions.assertEquals(
            emptyList<String>(),
            result
        )
    }

    @Test
    fun `should extract Entities from Report with one ReportQuery`() {
        // given
        val report = Report(
            id = "report-1",
            title = "Report 1",
            version = 1,
            transformations = emptyMap(),
            items = listOf(
                ReportItem.ReportQuery(
                    sql = "SELECT * FROM Children c JOIN School s WHERE c.school_id = s.id"
                )
            )
        )

        // when
        val result = service.getAffectedEntities(report)

        // then
        Assertions.assertEquals(
            listOf("Children", "School"),
            result
        )
    }

    @Test
    fun `should extract Entities from Report with multiple ReportQuery`() {
        // given
        val report = Report(
            id = "report-1",
            title = "Report 1",
            version = 1,
            transformations = emptyMap(),
            items = listOf(
                ReportItem.ReportQuery(
                    sql = "SELECT * FROM Children c JOIN School s WHERE c.school_id = s.id"
                ),
                ReportItem.ReportQuery(
                    sql = "SELECT c.name FROM Event c JOIN EventLocation e WHERE c.location = e.id"
                ),
                ReportItem.ReportQuery(
                    sql = "SELECT * FROM EventNotes"
                )
            )
        )

        // when
        val result = service.getAffectedEntities(report)

        // then
        Assertions.assertEquals(
            listOf("Children", "School", "Event", "EventLocation", "EventNotes"),
            result
        )
    }

    @Test
    fun `should extract Entities from Report with multiple ReportQuery and ReportGroup`() {
        // given
        val report = Report(
            id = "report-1",
            title = "Report 1",
            version = 1,
            transformations = emptyMap(),
            items = listOf(
                ReportItem.ReportQuery(
                    sql = "SELECT * FROM User"
                ),
                ReportItem.ReportGroup(
                    title = "foo",
                    items = listOf(
                        ReportItem.ReportQuery(
                            sql = "SELECT * FROM Children c JOIN School s WHERE c.school_id = s.id"
                        ),
                        ReportItem.ReportQuery(
                            sql = "SELECT c.name FROM Event c JOIN EventLocation e WHERE c.location = e.id"
                        ),
                    )
                ),
                ReportItem.ReportQuery(
                    sql = "SELECT * FROM EventNotes"
                )
            )
        )

        // when
        val result = service.getAffectedEntities(report)

        // then
        Assertions.assertEquals(
            listOf("User", "Children", "School", "Event", "EventLocation", "EventNotes"),
            result
        )
    }

    @ParameterizedTest
    @JsonFileSource(resources = ["/reporting/report/core/get-affected-entities-from-queries.json"])
    fun `should extract Entities from query`(data: JsonObject) {
        // given
        val query = data.getString("query").replace("\"", "")
        val expectedEntities = data.getJsonArray("entities").map { it.toString().replace("\"", "") }

        val report = Report(
            id = "report-1",
            title = "Report 1",
            version = 1,
            transformations = emptyMap(),
            items = listOf(
                ReportItem.ReportQuery(
                    sql = query
                ),
            )
        )

        // when
        val result = service.getAffectedEntities(report)

        // then
        Assertions.assertEquals(
            expectedEntities,
            result
        )
    }
}
