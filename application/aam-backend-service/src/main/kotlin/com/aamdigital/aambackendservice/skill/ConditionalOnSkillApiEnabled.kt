package com.aamdigital.aambackendservice.skill

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/**
 * Activates the annotated bean only when the skill-api feature flag is enabled.
 * Single source of truth for the `features.skill-api.enabled` property.
 *
 * Stack with [ConditionalOnSkillLabMode] for beans that additionally require
 * the SkillLab skill-api mode.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(
    prefix = "features.skill-api",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
annotation class ConditionalOnSkillApiEnabled
