package com.aamdigital.aambackendservice.reporting.report.sqs

import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.reporting.report.core.QueryStorage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.io.InputStream

data class QueryRequest(
    val query: String,
    val args: List<String>
)

@Service
class SqsQueryStorage(
    @Qualifier("sqs-client") private val sqsClient: RestClient,
    private val schemaService: SqsSchemaService,
) : QueryStorage {

    enum class SqsQueryStorageErrorCode : AamErrorCode {
        EMPTY_RESPONSE,
    }

    override fun executeQuery(query: QueryRequest): InputStream {
        val schemaPath = schemaService.getSchemaPath()
        schemaService.updateSchema()

        val response = sqsClient.post()
            .uri(schemaPath)
            .contentType(MediaType.APPLICATION_JSON)
            .body(query)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(Resource::class.java)

        if (response == null) {
            throw ExternalSystemException(
                message = "[SqsQueryStorage] Could not fetch response from SQS",
                code = SqsQueryStorageErrorCode.EMPTY_RESPONSE
            )
        }

        return response.inputStream
    }
}
