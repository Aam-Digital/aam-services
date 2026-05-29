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
 * How the template engine should aggregate the rendered output for an array input.
 *
 * Both modes use Carbone's native batch rendering (`batchSplitBy: "d"`); only the final
 * packaging differs.
 */
enum class RenderTemplateBatchMode {
    /** N independent files packaged in a single ZIP archive. */
    ZIP,

    /** N rendered files merged into a single multi-page PDF. */
    COMBINED
}

data class RenderTemplateBatchRequest(
    val templateRef: DomainReference,
    val bodyData: JsonNode,
    val mode: RenderTemplateBatchMode
) : UseCaseRequest

data class RenderTemplateBatchData(
    val file: InputStream,
    val responseHeaders: HttpHeaders
) : UseCaseData

enum class RenderTemplateBatchError : AamErrorCode {
    INTERNAL_SERVER_ERROR,
    FETCH_TEMPLATE_FAILED_ERROR,
    CREATE_RENDER_REQUEST_FAILED_ERROR,
    FETCH_RENDER_ID_REQUEST_FAILED_ERROR,
    PARSE_RESPONSE_ERROR,
    EMPTY_DATA_LIST_ERROR,
    INVALID_DATA_SHAPE_ERROR,

    /**
     * Template engine rejected the batch (e.g. array size exceeds `nbReportMaxPerBatch`,
     * or batch is disabled in the Carbone config). The error message returned from the
     * engine is propagated verbatim to the caller.
     */
    BATCH_REJECTED_ERROR,
    NOT_FOUND_ERROR
}

abstract class RenderTemplateBatchUseCase : DomainUseCase<RenderTemplateBatchRequest, RenderTemplateBatchData>()
