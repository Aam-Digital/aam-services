package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.reporting.report.sqs.QueryRequest
import java.io.InputStream

interface QueryStorage {
    fun executeQuery(query: QueryRequest): InputStream
}
