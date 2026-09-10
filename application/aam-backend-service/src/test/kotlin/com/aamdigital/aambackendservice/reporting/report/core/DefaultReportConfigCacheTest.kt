package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.reporting.report.Report
import com.aamdigital.aambackendservice.reporting.report.ReportItem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class DefaultReportConfigCacheTest {
    @Mock
    lateinit var reportStorage: ReportStorage

    private lateinit var cache: DefaultReportConfigCache

    private fun sqlReport(
        id: String,
        sql: String
    ) = Report(
        id = id,
        title = id,
        items = listOf(ReportItem.ReportQuery(sql = sql))
    )

    @BeforeEach
    fun setUp() {
        cache =
            DefaultReportConfigCache(
                reportStorage = reportStorage,
                // cheap and deterministic - the real regex analysis is what we want to cache
                reportQueryAnalyser = SimpleReportQueryAnalyser()
            )
    }

    @Test
    fun `should return only reports whose sql reads the changed entity type`() {
        // Given
        whenever(reportStorage.fetchAllReports(eq("sql")))
            .thenReturn(
                listOf(
                    sqlReport("ReportConfig:children", "SELECT * FROM Child"),
                    sqlReport("ReportConfig:schools", "SELECT * FROM School")
                )
            )

        // When
        val result = cache.findReportsForEntityType("Child")

        // Then
        assertThat(result.map { it.id }).containsExactly("ReportConfig:children")
    }

    @Test
    fun `should load report definitions only once for repeated reads`() {
        // Given
        whenever(reportStorage.fetchAllReports(eq("sql")))
            .thenReturn(listOf(sqlReport("ReportConfig:children", "SELECT * FROM Child")))

        // When
        cache.findReportsForEntityType("Child")
        cache.findReportsForEntityType("Child")
        cache.findReportsForEntityType("School")

        // Then
        verify(reportStorage, times(1)).fetchAllReports(eq("sql"))
    }

    @Test
    fun `should reload report definitions after being marked dirty`() {
        // Given
        whenever(reportStorage.fetchAllReports(eq("sql")))
            .thenReturn(listOf(sqlReport("ReportConfig:children", "SELECT * FROM Child")))
            .thenReturn(listOf(sqlReport("ReportConfig:children", "SELECT * FROM School")))

        // When
        val before = cache.findReportsForEntityType("Child")
        cache.markDirty()
        val after = cache.findReportsForEntityType("Child")

        // Then
        assertThat(before.map { it.id }).containsExactly("ReportConfig:children")
        assertThat(after).isEmpty()
        verify(reportStorage, times(2)).fetchAllReports(eq("sql"))
    }

    @Test
    fun `should propagate storage failures instead of serving an empty cache`() {
        // Given
        whenever(reportStorage.fetchAllReports(eq("sql")))
            .thenThrow(RuntimeException("couchdb unreachable"))

        // When / Then
        assertThat(
            runCatching { cache.findReportsForEntityType("Child") }.exceptionOrNull()
        ).hasMessage("couchdb unreachable")
    }
}
