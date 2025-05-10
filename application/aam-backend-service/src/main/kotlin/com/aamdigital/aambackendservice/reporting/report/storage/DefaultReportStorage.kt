package com.aamdigital.aambackendservice.reporting.report.storage

import com.aamdigital.aambackendservice.common.error.InternalServerException
import com.aamdigital.aambackendservice.common.error.InvalidArgumentException
import com.aamdigital.aambackendservice.common.error.NetworkException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.getQueryParamsAllDocs
import com.aamdigital.aambackendservice.common.couchdb.dto.CouchDbSearchResponse
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.report.Report
import com.aamdigital.aambackendservice.reporting.report.ReportItem
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorage
import com.aamdigital.aambackendservice.reporting.report.core.ReportStorageErrorCode
import com.aamdigital.aambackendservice.reporting.reportcalculation.storage.DefaultReportCalculationStorage.DefaultReportCalculationStorageError
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.util.LinkedMultiValueMap
import java.io.InterruptedIOException

/**
 * Class that handles fetching the ReportConfig: from couchdb and transform it into a [Report]
 * Can handle ReportConfigV1Entity and ReportConfigEntity (v2)
 */
class DefaultReportStorage(
    private val couchDbClient: CouchDbClient,
    private val objectMapper: ObjectMapper,
) : ReportStorage {

    companion object {
        private const val REPORT_DATABASE = "app"
    }

    override fun fetchAllReports(mode: String): List<Report> {
        // todo: paginate requests
        val response = couchDbClient.getDatabaseDocument(
            database = REPORT_DATABASE,
            documentId = "_all_docs",
            getQueryParamsAllDocs("ReportConfig"),
            CouchDbSearchResponse::class
        )

        if (response.rows.isEmpty()) {
            return emptyList()
        }

        return response.rows
            .filter { rawReportConfig ->
                isSqlReport(rawReportConfig.doc)
            }
            .map { rawReportConfig ->
                val version = try {
                    objectMapper
                        .readerFor(ObjectNode::class.java)
                        .readValue(
                            rawReportConfig.doc,
                            ReportConfigVersionEntity::class.java
                        ).version
                } catch (ex: Exception) {
                    throw handleException(ex)
                }

                anyEntityToReport(rawReportConfig.doc, version)
            }
    }

    @Throws(
        InvalidArgumentException::class,
        NetworkException::class,
        InternalServerException::class,
        NotFoundException::class
    )
    override fun fetchReport(reportRef: DomainReference): Report {
        val rawReportConfig = try {
            couchDbClient.getDatabaseDocument(
                database = REPORT_DATABASE,
                documentId = reportRef.id,
                queryParams = LinkedMultiValueMap(),
                kClass = ObjectNode::class
            )
        } catch (ex: Exception) {
            throw handleException(ex)
        }

        val version = try {
            objectMapper
                .readerFor(ObjectNode::class.java)
                .readValue(rawReportConfig, ReportConfigVersionEntity::class.java).version
        } catch (ex: Exception) {
            throw handleException(ex)
        }

        if (!isSqlReport(rawReportConfig)) {
            throw InvalidArgumentException(
                message = "Non SQL Reports are not supported",
                code = ReportStorageErrorCode.INVALID_REPORT_CONFIG
            )
        }

        return anyEntityToReport(rawReportConfig, version)
    }

    private fun anyEntityToReport(doc: ObjectNode, version: Int): Report {
        return when (val report = parseReportEntity(doc, version)) {
            is ReportConfigV1Entity -> toReport(report)
            is ReportConfigEntity -> toReport(report)
            else -> throw InternalServerException(
                message =
                "Invalid ReportConfig version. Only supports ReportConfigEntity and ReportConfigV1Entity",
                code = ReportStorageErrorCode.INVALID_REPORT_CONFIG
            ) // should not be possible
        }
    }

    private fun isSqlReport(objectNode: ObjectNode): Boolean {
        return objectNode.hasNonNull("mode") &&
                objectNode.get("mode").textValue() == "sql"
    }

    private fun parseReportEntity(rawReportConfig: ObjectNode, version: Int): Any {
        try {
            return when (version) {
                1 -> objectMapper
                    .readerFor(ObjectNode::class.java)
                    .readValue(rawReportConfig, ReportConfigV1Entity::class.java)

                2 -> objectMapper
                    .readerFor(ObjectNode::class.java)
                    .readValue(rawReportConfig, ReportConfigEntity::class.java)

                else -> throw InternalServerException(
                    message = "Invalid ReportConfig version. Only supports version 1 and 2",
                    code = ReportStorageErrorCode.INVALID_REPORT_CONFIG
                )
            }
        } catch (ex: Exception) {
            throw handleException(ex)
        }
    }

    private fun toReport(reportDoc: ReportConfigEntity): Report =
        Report(
            id = reportDoc.id,
            title = reportDoc.title,
            version = reportDoc.version,
            items = reportDoc.reportDefinition.map { asReportItem(it) },
            transformations = reportDoc.transformations,
        )

    private fun toReport(reportDoc: ReportConfigV1Entity): Report {
        val transformationMap = mutableMapOf<String, List<String>>()

        if (reportDoc.neededArgs.contains("to")) {
            transformationMap["to"] = listOf("SQL_TO_DATE")
        }

        if (reportDoc.neededArgs.contains("from")) {
            transformationMap["from"] = listOf("SQL_FROM_DATE")
        }

        return Report(
            id = reportDoc.id,
            title = reportDoc.title,
            version = reportDoc.version,
            items = listOf(
                ReportItem.ReportQuery(
                    sql = reportDoc.aggregationDefinition
                )
            ),
            transformations = transformationMap,
        )
    }


    private fun asReportItem(reportDefinitionDto: ReportDefinitionDto): ReportItem {
        if (!reportDefinitionDto.query.isNullOrEmpty()) {
            return ReportItem.ReportQuery(
                sql = reportDefinitionDto.query,
            )
        }

        if (!reportDefinitionDto.items.isNullOrEmpty() && !reportDefinitionDto.groupTitle.isNullOrEmpty()) {
            return ReportItem.ReportGroup(
                title = reportDefinitionDto.groupTitle,
                items = reportDefinitionDto.items.map {
                    asReportItem(it)
                }
            )
        }

        throw InternalServerException(
            message = "Invalid ReportConfig. Could not parse ReportItem",
            code = ReportStorageErrorCode.INVALID_REPORT_CONFIG
        )

    }

    private fun handleException(ex: Exception): Throwable {
        return when (ex) {
            is JacksonException -> InvalidArgumentException(
                message = "Could not parse Report to Entity",
                code = ReportStorageErrorCode.PARSING_ERROR,
                cause = ex
            )

            is InterruptedIOException -> NetworkException(
                message = ex.localizedMessage,
                cause = ex,
                code = DefaultReportCalculationStorageError.NETWORK_ERROR
            )

            else -> ex
        }
    }
}
