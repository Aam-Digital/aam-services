package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.domain.Report
import org.springframework.stereotype.Service
import java.util.regex.Pattern

@Service
class SimpleReportSchemaGenerator : ReportSchemaGenerator {
    override fun getTableNamesByQuery(query: String): List<String> {
        val pattern: Pattern = Pattern.compile("\\bas\\s+(\\w+)")
        val fieldNames: MutableList<String> = ArrayList()
        val matcher = pattern.matcher(query)

        while (matcher.find()) {
            fieldNames.add(matcher.group(1))
        }

        return fieldNames
    }

    override fun getAffectedEntities(report: Report): List<String> {
        val sqlFromTableRegex: Pattern = Pattern.compile("FROM\\s+(\\w+)")
        return sqlFromTableRegex
            .matcher(report.query)
            .results()
            .map { it.group(1) }
            .toList()
    }
}
