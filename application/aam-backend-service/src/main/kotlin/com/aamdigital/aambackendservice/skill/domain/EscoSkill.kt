package com.aamdigital.aambackendservice.skill.domain

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonProperty

enum class SkillUsage {
    ALMOST_NEVER,
    SOMETIMES,
    OFTEN,
    ALMOST_ALWAYS,
    ALWAYS,

    @JsonProperty("BI-WEEKLY")
    BI_WEEKLY,

    @JsonEnumDefaultValue
    UNKNOWN,
}

/**
 * Representation of an ESCO Skill
 * see: https://esco.ec.europa.eu/en/classification/skill_main
 */
data class EscoSkill(
    val escoUri: String,
    val usage: SkillUsage,
)

