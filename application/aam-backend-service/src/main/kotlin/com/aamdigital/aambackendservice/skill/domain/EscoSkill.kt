package com.aamdigital.aambackendservice.skill.domain

enum class SkillUsage {
    ALMOST_NEVER,
    SOMETIMES,
    OFTEN,
    ALMOST_ALWAYS,
    ALWAYS,
}

/**
 * Representation of an ESCO Skill
 * see: https://esco.ec.europa.eu/en/classification/skill_main
 */
data class EscoSkill(
    val escoUri: String,
    val usage: SkillUsage,
)

