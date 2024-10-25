package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.reporting.domain.Report
import com.aamdigital.aambackendservice.reporting.domain.ReportItem
import org.springframework.stereotype.Service
import java.util.regex.Pattern

@Service
class SimpleReportSchemaGenerator : ReportSchemaGenerator {
    override fun getTableNamesByQuery(query: String): List<String> {
        val selectFromPattern = "\\bSELECT\\b(.*?)\\bFROM\\b".toRegex()
        val asPattern = "\\s(as|AS)\\s+(\\w+)\\b".toRegex()

        val matchResult = selectFromPattern.find(query)

        if (matchResult != null) {
            val asFindings = asPattern.findAll(matchResult.groupValues[1])

            return asFindings.map { row -> row.groupValues[2] }.toList()
        } else {
            return emptyList()
        }
    }

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
