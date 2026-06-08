package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.TestErrorCode
import com.aamdigital.aambackendservice.common.domain.UseCaseOutcome
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.reporting.report.Report
import com.aamdigital.aambackendservice.reporting.report.ReportItem
import com.aamdigital.aambackendservice.reporting.report.core.QueryStorage
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorage
import com.aamdigital.aambackendservice.reporting.report.sqs.QueryRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculation
import com.aamdigital.aambackendservice.reporting.reportcalculation.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.reportcalculation.usecase.DefaultReportCalculationUseCase
import com.aamdigital.aambackendservice.reporting.transformation.SqlFromDateTransformation
import com.aamdigital.aambackendservice.reporting.transformation.SqlFromDateTransformation.Companion.DEFAULT_FROM_DATE
import com.aamdigital.aambackendservice.reporting.transformation.SqlToDateTransformation
import com.aamdigital.aambackendservice.reporting.transformation.SqlToDateTransformation.Companion.DEFAULT_TO_DATE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class DefaultReportCalculationUseCaseTest {
    private lateinit var service: DefaultReportCalculationUseCase

    @Mock
    lateinit var reportCalculationStorage: ReportCalculationStorage

    @Mock
    lateinit var reportStorage: ReportStorage

    @Mock
    lateinit var queryStorage: QueryStorage

    @BeforeEach
    fun setUp() {
        reset(reportCalculationStorage, reportStorage, queryStorage)
        service =
            DefaultReportCalculationUseCase(
                reportCalculationStorage = reportCalculationStorage,
                reportStorage = reportStorage,
                transformations =
                    listOf(
                        SqlFromDateTransformation(),
                        SqlToDateTransformation()
                    ),
                queryStorage = queryStorage
            )
    }

    private fun getPendingReportCalculation() =
        ReportCalculation(
            id = "ReportCalculation:1",
            report = DomainReference("Report:1"),
            status = ReportCalculationStatus.PENDING,
            args =
                mutableMapOf(
                    Pair("startDate", "2010-01-15T00:00:00.000Z"),
                    Pair("endDate", "2010-01-16")
                )
        )

    @Test
    fun `should return Failure when ReportCalculation does not exist`() {
        // given
        whenever(reportCalculationStorage.fetchReportCalculation(any())).thenAnswer {
            throw NotFoundException(
                message = "Not found",
                code = TestErrorCode.TEST_EXCEPTION
            )
        }

        // when
        val response =
            service.run(
                ReportCalculationRequest(
                    reportCalculationId = "does-not-exist"
                )
            )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        assertThat((response as UseCaseOutcome.Failure).errorCode)
            .isEqualTo(
                ReportCalculationError.REPORT_CALCULATION_NOT_FOUND
            )
        assertThat(response.cause).isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `should return Failure when Report does not exist`() {
        // given
        val reportCalculation = getPendingReportCalculation()

        whenever(
            reportCalculationStorage.fetchReportCalculation(
                eq(DomainReference("ReportCalculation:1"))
            )
        ).thenReturn(
            reportCalculation
        )

        whenever(
            reportStorage.fetchReport(
                eq(DomainReference("Report:1"))
            )
        ).thenAnswer {
            throw NotFoundException(
                message = "Not found",
                code = TestErrorCode.TEST_EXCEPTION
            )
        }

        // when
        val response =
            service.run(
                ReportCalculationRequest(
                    reportCalculationId = "ReportCalculation:1"
                )
            )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Failure::class.java)
        assertThat((response as UseCaseOutcome.Failure).errorCode)
            .isEqualTo(
                ReportCalculationError.REPORT_NOT_FOUND
            )
        assertThat(response.cause).isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `should apply argument transformations before sending to query service`() {
        // given
        val report =
            Report(
                id = "Report:1",
                title = "Report",
                items =
                    listOf(
                        ReportItem.ReportQuery(
                            sql = "SELECT * FROM foo WHERE time BETWEEN \$startDate and \$endDate"
                        )
                    ),
                transformations =
                    mapOf(
                        "endDate" to listOf("SQL_TO_DATE"),
                        "startDate" to listOf("SQL_FROM_DATE")
                    )
            )

        val reportCalculation =
            ReportCalculation(
                id = "ReportCalculation:1",
                report = DomainReference("Report:1"),
                status = ReportCalculationStatus.PENDING,
                args =
                    mutableMapOf(
                        Pair("startDate", "2010-01-15T00:00:00.000Z"),
                        Pair("endDate", "2010-01-16")
                    )
            )

        whenever(
            reportCalculationStorage.fetchReportCalculation(
                eq(DomainReference("ReportCalculation:1"))
            )
        ).thenReturn(reportCalculation)

        whenever(
            reportStorage.fetchReport(
                eq(DomainReference("Report:1"))
            )
        ).thenReturn(report)

        whenever(queryStorage.executeQuery(any())).thenReturn("[{}]".byteInputStream())

        whenever(reportCalculationStorage.storeCalculation(any()))
            .thenAnswer { i -> i.arguments[0] }

        whenever(reportCalculationStorage.addReportCalculationData(any(), any()))
            .thenAnswer { i -> i.arguments[0] }

        // when
        val response =
            service.run(
                ReportCalculationRequest(
                    reportCalculationId = reportCalculation.id
                )
            )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        assertEquals(
            reportCalculation,
            (response as UseCaseOutcome.Success).data.reportCalculation
        )

        verify(queryStorage).executeQuery(
            eq(
                QueryRequest(
                    query = "SELECT * FROM foo WHERE time BETWEEN ? and ?",
                    args =
                        listOf(
                            "2010-01-15",
                            "2010-01-16T23:59:59.999Z"
                        )
                )
            )
        )
    }

    @Test
    fun `should skip non-arg dollar signs in sql (e g json_extract path)`() {
        // given
        val report =
            Report(
                id = "Report:1",
                title = "Report",
                items =
                    listOf(
                        ReportItem.ReportQuery(
                            sql =
                                "SELECT *, json_extract(foo.children, '\$[0]') FROM foo " +
                                    "WHERE time BETWEEN \$startDate and \$endDate"
                        )
                    ),
                transformations =
                    mapOf(
                        "endDate" to listOf("SQL_TO_DATE"),
                        "startDate" to listOf("SQL_FROM_DATE")
                    )
            )

        val reportCalculation =
            ReportCalculation(
                id = "ReportCalculation:1",
                report = DomainReference("Report:1"),
                status = ReportCalculationStatus.PENDING,
                args =
                    mutableMapOf(
                        Pair("startDate", "2010-01-15T00:00:00.000Z"),
                        Pair("endDate", "2010-01-16")
                    )
            )

        whenever(
            reportCalculationStorage.fetchReportCalculation(
                eq(DomainReference("ReportCalculation:1"))
            )
        ).thenReturn(reportCalculation)

        whenever(
            reportStorage.fetchReport(
                eq(DomainReference("Report:1"))
            )
        ).thenReturn(report)

        whenever(queryStorage.executeQuery(any())).thenReturn("[{}]".byteInputStream())

        whenever(reportCalculationStorage.storeCalculation(any()))
            .thenAnswer { i -> i.arguments[0] }

        whenever(reportCalculationStorage.addReportCalculationData(any(), any()))
            .thenAnswer { i -> i.arguments[0] }

        // when
        val response =
            service.run(
                ReportCalculationRequest(
                    reportCalculationId = reportCalculation.id
                )
            )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        assertEquals(
            reportCalculation,
            (response as UseCaseOutcome.Success).data.reportCalculation
        )

        verify(queryStorage).executeQuery(
            eq(
                QueryRequest(
                    query = "SELECT *, json_extract(foo.children, '\$[0]') FROM foo WHERE time BETWEEN ? and ?",
                    args =
                        listOf(
                            "2010-01-15",
                            "2010-01-16T23:59:59.999Z"
                        )
                )
            )
        )
    }

    @Test
    fun `should apply multiple named argument transformations before sending to query service`() {
        // given
        val report =
            Report(
                id = "Report:1",
                title = "Report",
                items =
                    listOf(
                        ReportItem.ReportQuery(
                            sql =
                                "SELECT * FROM foo WHERE time BETWEEN \$startDate and \$endDate " +
                                    "AND date BETWEEN \$startDate AND \$endDate"
                        )
                    ),
                transformations =
                    mapOf(
                        "endDate" to listOf("SQL_TO_DATE"),
                        "startDate" to listOf("SQL_FROM_DATE")
                    )
            )

        val reportCalculation =
            ReportCalculation(
                id = "ReportCalculation:1",
                report = DomainReference("Report:1"),
                status = ReportCalculationStatus.PENDING,
                args =
                    mutableMapOf(
                        Pair("startDate", "2010-01-15T00:00:00.000Z"),
                        Pair("endDate", "2010-01-16")
                    )
            )

        whenever(
            reportCalculationStorage.fetchReportCalculation(
                eq(DomainReference("ReportCalculation:1"))
            )
        ).thenReturn(reportCalculation)

        whenever(
            reportStorage.fetchReport(
                eq(DomainReference("Report:1"))
            )
        ).thenReturn(report)

        whenever(queryStorage.executeQuery(any())).thenReturn("[{}]".byteInputStream())

        whenever(reportCalculationStorage.storeCalculation(any()))
            .thenAnswer { i -> i.arguments[0] }

        whenever(reportCalculationStorage.addReportCalculationData(any(), any()))
            .thenAnswer { i -> i.arguments[0] }

        // when
        val response =
            service.run(
                ReportCalculationRequest(
                    reportCalculationId = reportCalculation.id
                )
            )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        assertEquals(
            reportCalculation,
            (response as UseCaseOutcome.Success).data.reportCalculation
        )

        verify(queryStorage).executeQuery(
            eq(
                QueryRequest(
                    query = "SELECT * FROM foo WHERE time BETWEEN ? and ? AND date BETWEEN ? AND ?",
                    args =
                        listOf(
                            "2010-01-15",
                            "2010-01-16T23:59:59.999Z",
                            "2010-01-15",
                            "2010-01-16T23:59:59.999Z"
                        )
                )
            )
        )
    }

    @Test
    fun `should send query without args when report has no transformations`() {
        // given
        val report =
            Report(
                id = "Report:1",
                title = "Report",
                items =
                    listOf(
                        ReportItem.ReportQuery(
                            sql = "SELECT name FROM foo"
                        )
                    )
            )

        val reportCalculation =
            ReportCalculation(
                id = "ReportCalculation:1",
                report = DomainReference("Report:1"),
                status = ReportCalculationStatus.PENDING,
                args = mutableMapOf()
            )

        whenever(
            reportCalculationStorage.fetchReportCalculation(
                eq(DomainReference("ReportCalculation:1"))
            )
        ).thenReturn(reportCalculation)

        whenever(
            reportStorage.fetchReport(
                eq(DomainReference("Report:1"))
            )
        ).thenReturn(report)

        whenever(queryStorage.executeQuery(any())).thenReturn("[{}]".byteInputStream())

        whenever(reportCalculationStorage.storeCalculation(any()))
            .thenAnswer { i -> i.arguments[0] }

        whenever(reportCalculationStorage.addReportCalculationData(any(), any()))
            .thenAnswer { i -> i.arguments[0] }

        // when
        val response =
            service.run(
                ReportCalculationRequest(
                    reportCalculationId = reportCalculation.id
                )
            )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        verify(queryStorage).executeQuery(
            eq(
                QueryRequest(
                    query = "SELECT name FROM foo",
                    args = emptyList()
                )
            )
        )
    }

    @Test
    fun `should apply default values for arguments before sending to query service`() {
        // given
        val report =
            Report(
                id = "Report:1",
                title = "Report",
                items =
                    listOf(
                        ReportItem.ReportQuery(
                            sql = "SELECT * FROM foo WHERE time BETWEEN \$startDate and \$endDate"
                        )
                    ),
                transformations =
                    mapOf(
                        "startDate" to listOf("SQL_FROM_DATE"),
                        "endDate" to listOf("SQL_TO_DATE")
                    )
            )

        val reportCalculation =
            ReportCalculation(
                id = "ReportCalculation:1",
                report = DomainReference("Report:1"),
                status = ReportCalculationStatus.PENDING,
                args = mutableMapOf()
            )

        whenever(
            reportCalculationStorage.fetchReportCalculation(
                eq(DomainReference("ReportCalculation:1"))
            )
        ).thenReturn(reportCalculation)

        whenever(
            reportStorage.fetchReport(
                eq(DomainReference("Report:1"))
            )
        ).thenReturn(report)

        whenever(queryStorage.executeQuery(any())).thenReturn("[{}]".byteInputStream())

        whenever(reportCalculationStorage.storeCalculation(any()))
            .thenAnswer { i -> i.arguments[0] }

        whenever(reportCalculationStorage.addReportCalculationData(any(), any()))
            .thenAnswer { i -> i.arguments[0] }

        // when
        val response =
            service.run(
                ReportCalculationRequest(
                    reportCalculationId = reportCalculation.id
                )
            )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        assertEquals(
            reportCalculation,
            (response as UseCaseOutcome.Success).data.reportCalculation
        )

        verify(queryStorage).executeQuery(
            eq(
                QueryRequest(
                    query = "SELECT * FROM foo WHERE time BETWEEN ? and ?",
                    args =
                        listOf(
                            DEFAULT_FROM_DATE,
                            DEFAULT_TO_DATE
                        )
                )
            )
        )
    }

    @Test
    fun `should normalize legacy from-to args to startDate-endDate before applying transformations`() {
        // given
        val report =
            Report(
                id = "Report:1",
                title = "Report",
                items =
                    listOf(
                        ReportItem.ReportQuery(
                            sql = "SELECT * FROM foo WHERE time BETWEEN \$startDate and \$endDate"
                        )
                    ),
                transformations =
                    mapOf(
                        "startDate" to listOf("SQL_FROM_DATE"),
                        "endDate" to listOf("SQL_TO_DATE")
                    )
            )

        val reportCalculation =
            ReportCalculation(
                id = "ReportCalculation:1",
                report = DomainReference("Report:1"),
                status = ReportCalculationStatus.PENDING,
                args = mutableMapOf("from" to "2024-01-15T00:00:00Z", "to" to "2024-04-30T00:00:00Z")
            )

        whenever(
            reportCalculationStorage.fetchReportCalculation(
                eq(DomainReference("ReportCalculation:1"))
            )
        ).thenReturn(reportCalculation)

        whenever(
            reportStorage.fetchReport(
                eq(DomainReference("Report:1"))
            )
        ).thenReturn(report)

        whenever(queryStorage.executeQuery(any())).thenReturn("[{}]".byteInputStream())

        whenever(reportCalculationStorage.storeCalculation(any()))
            .thenAnswer { i -> i.arguments[0] }

        whenever(reportCalculationStorage.addReportCalculationData(any(), any()))
            .thenAnswer { i -> i.arguments[0] }

        // when
        val response =
            service.run(
                ReportCalculationRequest(
                    reportCalculationId = reportCalculation.id
                )
            )

        // then
        assertThat(response).isInstanceOf(UseCaseOutcome.Success::class.java)

        verify(queryStorage).executeQuery(
            eq(
                QueryRequest(
                    query = "SELECT * FROM foo WHERE time BETWEEN ? and ?",
                    args = listOf("2024-01-15", "2024-04-30T23:59:59.999Z")
                )
            )
        )
    }
}
