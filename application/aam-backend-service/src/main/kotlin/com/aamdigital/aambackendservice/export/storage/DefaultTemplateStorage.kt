package com.aamdigital.aambackendservice.export.storage

import com.aamdigital.aambackendservice.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.export.core.ExportTemplate
import com.aamdigital.aambackendservice.export.core.TemplateStorage
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.util.LinkedMultiValueMap
import reactor.core.publisher.Mono

data class ExportTemplateDto(
    @JsonProperty("_id")
    val id: String,
    val templateId: String,
    val title: String,
    val description: String,
    val applicableForEntityTypes: List<String>,
)

class DefaultTemplateStorage(
    private val couchDbClient: CouchDbClient
) : TemplateStorage {
    companion object {
        private const val TARGET_COUCH_DB = "app"
    }

    override fun fetchTemplate(template: DomainReference): Mono<ExportTemplate> {
        return couchDbClient.getDatabaseDocument(
            database = TARGET_COUCH_DB,
            documentId = template.id,
            queryParams = LinkedMultiValueMap(mapOf()),
            kClass = ExportTemplateDto::class
        ).map {
            toEntity(it)
        }
    }

    private fun toEntity(dto: ExportTemplateDto): ExportTemplate = ExportTemplate(
        id = dto.id,
        templateId = dto.templateId,
        title = dto.title,
        description = dto.description,
        applicableForEntityTypes = dto.applicableForEntityTypes
    )
}
