package com.aamdigital.aambackendservice.report.core

import com.aamdigital.aambackendservice.report.sqs.QueryRequest
import com.aamdigital.aambackendservice.report.sqs.QueryResult
import reactor.core.publisher.Mono

interface QueryStorage {
    fun executeQuery(query: QueryRequest): Mono<QueryResult>
}
