package com.aamdigital.aambackendservice.reporting.report.sqs

import com.aamdigital.aambackendservice.common.domain.EntityAttributeType

/**
 * ConfigurableEnum documents are defined in code (not in Config:CONFIG_ENTITY), so their
 * SQS table and label-lookup view are hard-wired here rather than derived from app config.
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
            "json_extract(o.value, '$.label') AS label " +
            "FROM ConfigurableEnum ec, json_each(ec.\"values\") o"
}
