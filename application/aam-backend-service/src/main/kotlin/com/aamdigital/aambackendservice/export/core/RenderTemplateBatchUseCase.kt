package com.aamdigital.aambackendservice.export.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.domain.DomainUseCase
import com.aamdigital.aambackendservice.common.domain.UseCaseData
import com.aamdigital.aambackendservice.common.domain.UseCaseRequest
import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.HttpHeaders
import java.io.InputStream

/**
 * How the use case should aggregate the rendered output for an array input.
 */
enum class RenderTemplateBatchMode {
    /**
     * Render each record independently and bundle the resulting files into a single ZIP.
     * Existing single-record templates work as-is.
     */
    ZIP,

    /**
     * Forward the array as-is to the template engine and return whatever it produces
     * (typically one multi-page file when the template uses array placeholders).
     */
    COMBINED
}

data class RenderTemplateBatchRequest(
    val templateRef: DomainReference,
    val bodyData: JsonNode,
    val mode: RenderTemplateBatchMode
) : UseCaseRequest

data class RenderTemplateBatchData(
    val file: InputStream,
    val responseHeaders: HttpHeaders,
    /**
     * Indices of records (in the order they appeared in the request `data` array)
     * for which rendering failed. Only populated in [RenderTemplateBatchMode.ZIP].
     */
    val failedIndices: List<Int> = emptyList()
) : UseCaseData

enum class RenderTemplateBatchError : AamErrorCode {
    INTERNAL_SERVER_ERROR,
    FETCH_TEMPLATE_FAILED_ERROR,
    CREATE_RENDER_REQUEST_FAILED_ERROR,
    FETCH_RENDER_ID_REQUEST_FAILED_ERROR,
    PARSE_RESPONSE_ERROR,
    EMPTY_DATA_LIST_ERROR,
    INVALID_DATA_SHAPE_ERROR,
    ALL_RECORDS_FAILED_ERROR,
    NOT_FOUND_ERROR
}

abstract class RenderTemplateBatchUseCase : DomainUseCase<RenderTemplateBatchRequest, RenderTemplateBatchData>()
