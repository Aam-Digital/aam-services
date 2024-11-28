package com.aamdigital.aambackendservice.skill.di

import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesUseCase
import com.aamdigital.aambackendservice.skill.core.SearchUserProfileUseCase
import com.aamdigital.aambackendservice.skill.core.SkillStorage
import com.aamdigital.aambackendservice.skill.core.SqlSearchUserProfileUseCase
import com.aamdigital.aambackendservice.skill.core.SyncUserProfileUseCase
import com.aamdigital.aambackendservice.skill.core.UserProfileMatcher
import com.aamdigital.aambackendservice.skill.core.UserProfileUpdatePublisher
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileRepository
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileSyncRepository
import com.aamdigital.aambackendservice.skill.skilllab.SkillLabClient
import com.aamdigital.aambackendservice.skill.skilllab.SkillLabFetchUserProfileUpdatesUseCase
import com.aamdigital.aambackendservice.skill.skilllab.SkillLabSkillStorage
import com.aamdigital.aambackendservice.skill.skilllab.SkillLabSyncUserProfileUseCase
import com.aamdigital.aambackendservice.skill.skilllab.SkillLabUserProfileMatcher
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient


@ConfigurationProperties("skilllab-api-client-configuration")
@ConditionalOnProperty(
    prefix = "skilllab-api-client-configuration",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class AamRenderApiClientConfiguration(
    val basePath: String,
    val apiKey: String,
    val responseTimeoutInSeconds: Int = 30,
)

@Configuration
class SkillConfiguration {

    @Bean(name = ["skilllab-api-client"])
    @ConditionalOnProperty(
        prefix = "skilllab-api-client-configuration",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false
    )
    fun skillLabApiClient(
        configuration: AamRenderApiClientConfiguration
    ): RestClient {
        val clientBuilder = RestClient.builder().baseUrl(configuration.basePath)

        clientBuilder.defaultRequest { request ->
            request.headers {
                it.set(HttpHeaders.AUTHORIZATION, "Bearer ${configuration.apiKey}")
            }
        }

        clientBuilder.requestFactory(SimpleClientHttpRequestFactory().apply {
            setReadTimeout(configuration.responseTimeoutInSeconds * 1000)
            setConnectTimeout(configuration.responseTimeoutInSeconds * 1000)
        })

        return clientBuilder.build()
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "skilllab-api-client-configuration",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false
    )
    fun skillLabClient(
        @Qualifier("skilllab-api-client") restClient: RestClient,
        objectMapper: ObjectMapper,
    ): SkillLabClient = SkillLabClient(
        http = restClient,
        objectMapper = objectMapper,
    )

    @Bean
    @ConditionalOnProperty(
        prefix = "skilllab-api-client-configuration",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false
    )
    fun skillLabUserProfileStorage(
        @Qualifier("skilllab-api-client") restClient: RestClient,
    ): SkillStorage = SkillLabSkillStorage(
        http = restClient
    )

    @Bean
    @ConditionalOnProperty(
        prefix = "skilllab-api-client-configuration",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false
    )
    fun skillLabUserProfileMatcher(
        @Qualifier("skilllab-api-client") restClient: RestClient,
    ): UserProfileMatcher = SkillLabUserProfileMatcher(
        http = restClient
    )

    @Bean
    @ConditionalOnProperty(
        prefix = "skilllab-api-client-configuration",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false
    )
    fun skillLabFetchUserProfileUpdatedUseCase(
        skillLabClient: SkillLabClient,
        skillLabUserProfileSyncRepository: SkillLabUserProfileSyncRepository,
        userProfileUpdatePublisher: UserProfileUpdatePublisher,
    ): FetchUserProfileUpdatesUseCase = SkillLabFetchUserProfileUpdatesUseCase(
        skillLabClient = skillLabClient,
        skillLabUserProfileSyncRepository = skillLabUserProfileSyncRepository,
        userProfileUpdatePublisher = userProfileUpdatePublisher,
    )

    @Bean
    @ConditionalOnProperty(
        prefix = "skilllab-api-client-configuration",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false
    )
    fun skillLabSyncUserProfileUseCase(
        skillLabClient: SkillLabClient,
        skillLabUserProfileRepository: SkillLabUserProfileRepository,
    ): SyncUserProfileUseCase = SkillLabSyncUserProfileUseCase(
        skillLabClient = skillLabClient,
        skillLabUserProfileRepository = skillLabUserProfileRepository,
    )

    @Bean
    fun sqlSearchUserProfileUseCase(
        skillLabUserProfileRepository: SkillLabUserProfileRepository,
    ): SearchUserProfileUseCase = SqlSearchUserProfileUseCase(
        userProfileRepository = skillLabUserProfileRepository,
    )
}
