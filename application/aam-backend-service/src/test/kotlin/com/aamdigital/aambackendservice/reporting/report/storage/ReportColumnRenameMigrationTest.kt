package com.aamdigital.aambackendservice.reporting.report.storage

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.reporting.report.Report
import com.aamdigital.aambackendservice.reporting.report.ReportItem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class ReportColumnRenameMigrationTest {
    @Mock
    lateinit var couchDbClient: CouchDbClient

    private val migration by lazy { ReportColumnRenameMigration(couchDbClient) }

    @Test
    fun `should rewrite all deprecated column names but leave prefixed and custom names untouched`() {
        val rewritten =
            migration.rewriteSql(
                "SELECT c.created_at, c.created_by, c.updated_at, c.updated_by, " +
                    "c._created_at, c.my_created_at, c.created_at_old " +
                    "FROM Child c WHERE created_at BETWEEN \$startDate AND \$endDate"
            )

        assertThat(rewritten).isEqualTo(
            "SELECT c._created_at, c._created_by, c._updated_at, c._updated_by, " +
                "c._created_at, c.my_created_at, c.created_at_old " +
                "FROM Child c WHERE _created_at BETWEEN \$startDate AND \$endDate"
        )
        // idempotent: applying the rewrite again changes nothing
        assertThat(migration.rewriteSql(rewritten)).isEqualTo(rewritten)
    }

    @Test
    fun `should migrate queries in nested groups and return same instance when nothing changes`() {
        val report =
            Report(
                id = "ReportConfig:test",
                title = "Test",
                items =
                    listOf(
                        ReportItem.ReportGroup(
                            title = "group",
                            items = listOf(ReportItem.ReportQuery(sql = "SELECT created_at FROM Child"))
                        )
                    )
            )

        val migrated = migration.migrate(report)

        val group = migrated.items[0] as ReportItem.ReportGroup
        assertThat((group.items[0] as ReportItem.ReportQuery).sql).isEqualTo("SELECT _created_at FROM Child")
        // a report without deprecated names is passed through unchanged
        assertThat(migration.migrate(migrated)).isSameAs(migrated)
    }
}
