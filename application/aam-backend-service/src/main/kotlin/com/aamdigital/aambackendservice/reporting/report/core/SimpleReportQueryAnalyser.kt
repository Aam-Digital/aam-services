package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.reporting.domain.Report
import com.aamdigital.aambackendservice.reporting.domain.ReportItem
import java.util.regex.Pattern

class SimpleReportQueryAnalyser : ReportQueryAnalyser {

    companion object {
        private val EXTRACT_ENTITIES_FROM_SQL_REGEX: Pattern = Pattern.compile(
            "(FROM|from|JOIN|join)\\s+(`\\w+.+\\w+\\s*`|(\\[)\\w+.+\\w+\\s*(])|\\w+\\s*\\.+\\s*\\w*|\\w+\\b)"
        )
    }

    override fun getAffectedEntities(report: Report): List<String> {
        return extractEntitiesFromItems(report.items)
    }

    private fun extractEntitiesFromItems(items: List<ReportItem>): List<String> = items.flatMap { reportItem ->
        when (reportItem) {
            is ReportItem.ReportGroup -> extractEntitiesFromItems(reportItem.items)
            is ReportItem.ReportQuery -> EXTRACT_ENTITIES_FROM_SQL_REGEX
                .matcher(reportItem.sql)
                .results()
                .map { it.group(2) }
                .toList()
        }
    }
}
