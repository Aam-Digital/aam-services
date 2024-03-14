package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.reporting.report.sqs.QueryRequest
import com.aamdigital.aambackendservice.reporting.report.sqs.QueryResult
import reactor.core.publisher.Mono

interface QueryStorage {
    fun executeQuery(query: QueryRequest): Mono<QueryResult>
}
