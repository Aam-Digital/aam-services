package com.aamdigital.aambackendservice.export.core

/**
 * Represents a template configuration used for exporting data.
 * this entity is normally managed in the frontend.
 *
 * @property id Unique identifier of the export template.
 * @property templateId Identifier of the corresponding template in the template engine.
 * @property targetFileName Name of the target file to be generated.
 * @property title Title of the export template, visible in frontend
 * @property description (optional) Description providing details about the export template, visible in frontend.
 * @property applicableForEntityTypes List of entity types for which this export template is applicable.
 */
data class TemplateExport(
    val id: String,
    val templateId: String,
    val targetFileName: String,
    val title: String,
    val description: String? = null,
    val applicableForEntityTypes: List<String>,
)
