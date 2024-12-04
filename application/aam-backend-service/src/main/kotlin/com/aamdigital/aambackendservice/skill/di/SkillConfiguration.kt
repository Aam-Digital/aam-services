package com.aamdigital.aambackendservice.skill.di

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties


/**
 * Configures behaviour of the skill-api.
 *
 * @mode skilllab
 *      Use SkillLab as SkillProvider, needs skilllab-api-client-configuration
 *
 * @mode disabled
 *      Disable skill-api. Endpoints are not reachable, nothing is imported.
 *
 */
@ConfigurationProperties("features.skill-api")
class FeatureConfigurationSkillApi(
    val mode: String,
)

@ConfigurationProperties("skilllab-api-client-configuration")
@ConditionalOnProperty(
    prefix = "features.skill-api",
    name = ["mode"],
    havingValue = "skilllab",
    matchIfMissing = false
)
class SkillLabApiClientConfiguration(
    val basePath: String,
    val apiKey: String,
    val projectId: String,
    val responseTimeoutInSeconds: Int = 30,
)
