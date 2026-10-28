package com.aamdigital.aambackendservice.reporting.report.sqs

import com.aamdigital.aambackendservice.common.domain.EntityAttributeType

/**
 * ConfigurableEnum documents are defined in code (not in Config:CONFIG_ENTITY), so their
 * SQS table and label-lookup view are hard-wired here rather than derived from app config
 * (together with the SiteSettings fields that view needs).
 */
object ConfigurableEnumSchema {
    /**
     * Name of the hard-wired table exposing raw ConfigurableEnum documents
     * (one row per enum, options as a JSON array in the "values" column).
     */
    const val TABLE = "ConfigurableEnum"

    val FIELDS =
        TableFields(
            mapOf(
                "_id" to EntityAttributeType(field = "_id", type = "TEXT"),
                "values" to EntityAttributeType(field = "values", type = "TEXT")
            )
        )

    /**
     * Doc id of the SiteSettings document, whose `defaultLanguage` is used to pick
     * one language of multi-lingual option labels.
     */
    private const val SITE_SETTINGS_ID = "SiteSettings:global"

    /**
     * Hard-wired fields of the SiteSettings table, which [OPTION_VIEW] reads the
     * system's default language from. Merged into a manually configured entity:SiteSettings.
     */
    const val SITE_SETTINGS_TABLE = "SiteSettings"
    val SITE_SETTINGS_FIELDS =
        mapOf(
            "_id" to EntityAttributeType(field = "_id", type = "TEXT"),
            "defaultLanguage" to EntityAttributeType(field = "defaultLanguage", type = "TEXT")
        )

    /**
     * Option label as a single text.
     * In multi-lingual systems the label is an object of translations (e.g. `{"en-US": "male", "de": "männlich"}`),
     * from which the system's default language is picked, falling back to English and then any non-empty translation.
     */
    private const val OPTION_LABEL =
        "CASE WHEN json_type(o.value, '$.label') = 'object' THEN COALESCE(" +
            "NULLIF(json_extract(o.value, '$.label.\"' || " +
            "(SELECT s.defaultLanguage FROM SiteSettings s WHERE s._id = '$SITE_SETTINGS_ID') || '\"'), ''), " +
            "NULLIF(json_extract(o.value, '$.label.\"en-US\"'), ''), " +
            "NULLIF(json_extract(o.value, '$.label.en'), ''), " +
            "(SELECT t.value FROM json_each(o.value, '$.label') t " +
            "WHERE t.type = 'text' AND t.value <> '' LIMIT 1)" +
            ") ELSE json_extract(o.value, '$.label') END"

    /**
     * One row per enum option, so report queries can translate stored option ids
     * into human-readable labels with a plain JOIN instead of json_each boilerplate:
     *
     * SELECT c.name, COALESCE(g.label, c.gender) AS gender
     * FROM Child c
     * LEFT JOIN ConfigurableEnumOption g
     *   ON g.enum_id = 'genders' AND g.option_id = c.gender
     *
     * SQS executes any statement listed in sql.indexes, which lets us ship this view
     * as part of the schema definition.
     */
    const val OPTION_VIEW =
        "CREATE VIEW IF NOT EXISTS ConfigurableEnumOption AS " +
            "SELECT REPLACE(ec._id, 'ConfigurableEnum:', '') AS enum_id, " +
            "json_extract(o.value, '$.id') AS option_id, " +
            "$OPTION_LABEL AS label " +
            "FROM ConfigurableEnum ec, json_each(ec.\"values\") o"
}
