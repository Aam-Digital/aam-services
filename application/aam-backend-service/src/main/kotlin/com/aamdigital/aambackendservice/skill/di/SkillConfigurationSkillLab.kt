package com.aamdigital.aambackendservice.skill.di

import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesUseCase
import com.aamdigital.aambackendservice.skill.core.SearchUserProfileUseCase
import com.aamdigital.aambackendservice.skill.core.SqlSearchUserProfileUseCase
import com.aamdigital.aambackendservice.skill.core.SyncUserProfileUseCase
import com.aamdigital.aambackendservice.skill.core.UserProfileUpdatePublisher
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileRepository
import com.aamdigital.aambackendservice.skill.repository.SkillLabUserProfileSyncRepository
import com.aamdigital.aambackendservice.skill.skilllab.SkillLabClient
import com.aamdigital.aambackendservice.skill.skilllab.SkillLabFetchUserProfileUpdatesUseCase
import com.aamdigital.aambackendservice.skill.skilllab.SkillLabSyncUserProfileUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient


@Configuration
@ConditionalOnProperty(
    prefix = "features.skill-api",
    name = ["mode"],
    havingValue = "skilllab",
    matchIfMissing = false
)
class SkillConfigurationSkillLab {

    @Bean(name = ["skilllab-api-client"])
    fun skillLabApiClient(
        configuration: SkillLabApiClientConfiguration
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
    fun skillLabClient(
        @Qualifier("skilllab-api-client") restClient: RestClient,
        objectMapper: ObjectMapper,
    ): SkillLabClient = SkillLabClient(
        http = restClient,
        objectMapper = objectMapper,
    )

    @Bean
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
    fun skillLabSyncUserProfileUseCase(
        skillLabClient: SkillLabClient,
        skillLabUserProfileRepository: SkillLabUserProfileRepository,
        objectMapper: ObjectMapper,
    ): SyncUserProfileUseCase = SkillLabSyncUserProfileUseCase(
        skillLabClient = skillLabClient,
        skillLabUserProfileRepository = skillLabUserProfileRepository,
        objectMapper = objectMapper,
    )

    @Bean
    fun sqlSearchUserProfileUseCase(
        skillLabUserProfileRepository: SkillLabUserProfileRepository,
        objectMapper: ObjectMapper,
    ): SearchUserProfileUseCase = SqlSearchUserProfileUseCase(
        userProfileRepository = skillLabUserProfileRepository,
        objectMapper = objectMapper,
    )
}
