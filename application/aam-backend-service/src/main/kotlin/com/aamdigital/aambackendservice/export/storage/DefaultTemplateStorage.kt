package com.aamdigital.aambackendservice.export.storage

import com.aamdigital.aambackendservice.common.error.AamErrorCode
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.NetworkException
import com.aamdigital.aambackendservice.common.error.NotFoundException
import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.export.core.TemplateExport
import com.aamdigital.aambackendservice.export.core.TemplateStorage
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.util.LinkedMultiValueMap
import java.io.InterruptedIOException

data class TemplateExportDto(
    @JsonProperty("_id")
    val id: String,
    val templateId: String,
    val targetFileName: String,
    val title: String,
    val description: String? = null,
    val applicableForEntityTypes: List<String>,
)

class DefaultTemplateStorage(
    private val couchDbClient: CouchDbClient
) : TemplateStorage {
    companion object {
        private const val TARGET_COUCH_DB = "app"
    }

    enum class DefaultTemplateStorageErrorCode : AamErrorCode {
        IO_NETWORK_ERROR
    }

    @Throws(
        NotFoundException::class,
        ExternalSystemException::class,
        NetworkException::class
    )
    override fun fetchTemplate(template: DomainReference): TemplateExport {
        val document = try {
            couchDbClient.getDatabaseDocument(
                database = TARGET_COUCH_DB,
                documentId = template.id,
                queryParams = LinkedMultiValueMap(mapOf()),
                kClass = TemplateExportDto::class
            )
        } catch (ex: InterruptedIOException) {
            throw NetworkException(
                cause = ex,
                message = ex.localizedMessage,
                code = DefaultTemplateStorageErrorCode.IO_NETWORK_ERROR
            )
        }

        return toEntity(document)
    }

    private fun toEntity(dto: TemplateExportDto): TemplateExport = TemplateExport(
        id = dto.id,
        targetFileName = dto.targetFileName,
        templateId = dto.templateId,
        title = dto.title,
        description = dto.description,
        applicableForEntityTypes = dto.applicableForEntityTypes
    )
}
