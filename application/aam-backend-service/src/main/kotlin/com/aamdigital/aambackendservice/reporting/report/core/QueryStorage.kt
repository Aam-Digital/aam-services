package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.reporting.report.sqs.QueryRequest
import org.springframework.core.io.buffer.DataBuffer
import reactor.core.publisher.Flux

interface QueryStorage {
    fun executeQuery(query: QueryRequest): Flux<DataBuffer>
}
