package com.aamdigital.aambackendservice.reporting.reportcalculation.core

import com.aamdigital.aambackendservice.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.domain.Report
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculation
import com.aamdigital.aambackendservice.reporting.domain.ReportCalculationStatus
import com.aamdigital.aambackendservice.reporting.domain.ReportSchema
import com.aamdigital.aambackendservice.reporting.report.core.QueryStorage
import com.aamdigital.aambackendservice.reporting.report.sqs.QueryRequest
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.DefaultReportCalculator.Companion.DEFAULT_FROM_DATE
import com.aamdigital.aambackendservice.reporting.reportcalculation.core.DefaultReportCalculator.Companion.DEFAULT_TO_DATE
import com.aamdigital.aambackendservice.reporting.storage.DefaultReportStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.keycloak.common.util.Base64.InputStream
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

@ExtendWith(MockitoExtension::class)
class DefaultReportCalculatorTest {

    private lateinit var service: DefaultReportCalculator

    @Mock
    lateinit var reportStorage: DefaultReportStorage

    @Mock
    lateinit var queryStorage: QueryStorage

    @Mock
    lateinit var couchDbClient: CouchDbClient

    @BeforeEach
    fun setUp() {
        reset(reportStorage, queryStorage, couchDbClient)
        service = DefaultReportCalculator(reportStorage, queryStorage, couchDbClient)
    }

    @Test
    fun `should format query arguments when calling queryStorage`() {
        // given
        val report = Report(
            id = "Report:1",
            name = "Report",
            mode = "sql",
            query = "SELECT * FROM foo",
            neededArgs = listOf(
                "from",
                "to"
            ),
            schema = ReportSchema(
                fields = listOf("foo", "bar")
            ),
        )

        val reportCalculation = ReportCalculation(
            id = "ReportCalculation:1",
            report = DomainReference("Report:1"),
            status = ReportCalculationStatus.PENDING,
            args = mutableMapOf(
                Pair("from", "2010-01-15T00:00:00.000Z"),
                Pair("to", "2010-01-16"),
            )
        )

        whenever(reportStorage.fetchReport(reportCalculation.report))
            .thenAnswer {
                Optional.of(report)
            }

        whenever(queryStorage.executeQuery(any()))
            .thenAnswer {
                InputStream.nullInputStream()
            }

        configureCouchDbClientDocSuccessResponse(reportCalculation.id)

        // when
        val response = service.calculate(
            reportCalculation = reportCalculation
        )

        // then
        assertThat(response).isInstanceOf(ReportCalculation::class.java)
        Assertions.assertEquals(reportCalculation, response)
        verify(queryStorage).executeQuery(
            eq(
                QueryRequest(
                    query = "SELECT * FROM foo",
                    args = listOf(
                        "2010-01-15", "2010-01-16T23:59:59.999Z"
                    )
                )
            )
        )
    }

    @Test
    fun `should set default query arguments when calling queryStorage`() {
        // given
        val report = Report(
            id = "Report:1",
            name = "Report",
            mode = "sql",
            query = "SELECT * FROM foo",
            neededArgs = listOf(
                "from",
                "to"
            ),
            schema = ReportSchema(
                fields = listOf("foo", "bar")
            ),
        )

        val reportCalculation = ReportCalculation(
            id = "ReportCalculation:1",
            report = DomainReference("Report:1"),
            status = ReportCalculationStatus.PENDING,
            args = mutableMapOf()
        )

        whenever(reportStorage.fetchReport(reportCalculation.report))
            .thenAnswer {
                Optional.of(report)
            }

        whenever(queryStorage.executeQuery(any()))
            .thenAnswer {
                InputStream.nullInputStream()
            }

        configureCouchDbClientDocSuccessResponse(reportCalculation.id)

        // when
        val response = service.calculate(
            reportCalculation = reportCalculation
        )

        // then
        assertThat(response).isInstanceOf(ReportCalculation::class.java)
        Assertions.assertEquals(reportCalculation, response)
        verify(queryStorage).executeQuery(
            eq(
                QueryRequest(
                    query = "SELECT * FROM foo",
                    args = listOf(
                        DEFAULT_FROM_DATE, DEFAULT_TO_DATE
                    )
                )
            )
        )
    }

    private fun configureCouchDbClientDocSuccessResponse(id: String) {
        whenever(couchDbClient.putAttachment(any(), any(), any(), any()))
            .thenAnswer {
                DocSuccess(
                    id = id,
                    ok = true,
                    rev = "foo"
                )
            }
    }
}
