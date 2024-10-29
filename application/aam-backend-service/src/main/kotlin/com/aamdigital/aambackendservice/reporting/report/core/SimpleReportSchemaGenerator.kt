package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.reporting.domain.Report
import com.aamdigital.aambackendservice.reporting.domain.ReportItem
import java.util.regex.Pattern

class SimpleReportSchemaGenerator : ReportSchemaGenerator {
    override fun getAffectedEntities(report: Report): List<String> {
        val sqlFromTableRegex: Pattern = Pattern.compile("FROM\\s+(\\w+)")
        val query: ReportItem.ReportQuery =
            report.items.first { it is ReportItem.ReportQuery } as ReportItem.ReportQuery
        return sqlFromTableRegex
            .matcher(query.sql) // todo analyse all queries here
            .results()
            .map { it.group(1) }
            .toList()
    }
}
