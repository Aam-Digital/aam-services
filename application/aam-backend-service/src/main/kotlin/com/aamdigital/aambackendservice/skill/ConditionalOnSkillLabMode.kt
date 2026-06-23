package com.aamdigital.aambackendservice.skill

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/**
 * Activates the annotated bean only when the skill-api mode is set to `skilllab`.
 * Single source of truth for `features.skill-api.mode=skilllab`.
 *
 * Typically stacked with [ConditionalOnSkillApiEnabled].
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(
    prefix = "features.skill-api",
    name = ["mode"],
    havingValue = "skilllab",
    matchIfMissing = false,
)
annotation class ConditionalOnSkillLabMode
