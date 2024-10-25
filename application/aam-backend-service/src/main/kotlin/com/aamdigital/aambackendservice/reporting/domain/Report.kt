package com.aamdigital.aambackendservice.reporting.domain

/**
 * Represents a SQL based Report
 *
 * @param id unique identifier
 * @param title human-readable title of the Report
 * @param version version of the ReportItem.ReportQuery sql format
 * @param items list of ReportItems - can include nested ReportItems
 * @param transformations list of data transformations, applied to passed arguments
 *
 */
data class Report(
    val id: String,
    val title: String,
    val version: Int,
    val items: List<ReportItem>,
    val transformations: Map<String, List<String>> = mutableMapOf(),
)

/**
 * ReportItem representation. A report contains multiple ReportItems.
 * Sealed class - Can either be a ReportQuery or a ReportGroup
 */
sealed class ReportItem {

    /**
     * Represents a SQL statement
     *
     * @param sql SQL statement with possible placeholders, starting with '$' char
     */
    data class ReportQuery(
        val sql: String,
    ) : ReportItem()

    /**
     * Represents a Group of ReportItem for multi-level reports
     *
     * @param title title of this group
     * @param items list of ReportItems
     */
    data class ReportGroup(
        val title: String,
        val items: List<ReportItem>
    ) : ReportItem()
}
