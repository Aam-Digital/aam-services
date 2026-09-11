package com.aamdigital.aambackendservice.skill.di

import com.aamdigital.aambackendservice.common.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.common.couchdb.core.DatabaseRequest
import com.aamdigital.aambackendservice.skill.ConditionalOnSkillApiEnabled
import com.aamdigital.aambackendservice.skill.ConditionalOnSkillLabMode
import com.aamdigital.aambackendservice.skill.core.FetchUserProfileUpdatesUseCase
import com.aamdigital.aambackendservice.skill.core.InMemorySearchUserProfileUseCase
import com.aamdigital.aambackendservice.skill.core.SearchUserProfileUseCase
import com.aamdigital.aambackendservice.skill.core.SyncUserProfileUseCase
import com.aamdigital.aambackendservice.skill.core.UserProfileUpdatePublisher
import com.aamdigital.aambackendservice.skill.repository.CouchDbSkillUserProfileRepository
import com.aamdigital.aambackendservice.skill.repository.SkillUserProfileRepository
import com.aamdigital.aambackendservice.skill.skilllab.SkillLabClient
import com.aamdigital.aambackendservice.skill.skilllab.SkillLabFetchUserProfileUpdatesUseCase
import com.aamdigital.aambackendservice.skill.skilllab.SkillLabSyncUserProfileUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
@ConditionalOnSkillApiEnabled
@ConditionalOnSkillLabMode
class SkillConfigurationSkillLab {
    @Bean(name = ["skilllab-api-client"])
    fun skillLabApiClient(configuration: SkillLabApiClientConfiguration): RestClient {
        val clientBuilder = RestClient.builder().baseUrl(configuration.basePath)

        clientBuilder.defaultRequest { request ->
            request.headers {
                it.set(HttpHeaders.AUTHORIZATION, "Bearer ${configuration.apiKey}")
            }
        }

        clientBuilder.requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setReadTimeout(configuration.responseTimeoutInSeconds * 1000)
                setConnectTimeout(configuration.responseTimeoutInSeconds * 1000)
            }
        )

        return clientBuilder.build()
    }

    @Bean
    fun skillLabClient(
        @Qualifier("skilllab-api-client") restClient: RestClient,
        objectMapper: ObjectMapper
    ): SkillLabClient =
        SkillLabClient(
            http = restClient,
            objectMapper = objectMapper
        )

    @Bean
    fun skillUserProfileDatabaseRequest(): DatabaseRequest =
        DatabaseRequest(CouchDbSkillUserProfileRepository.SKILL_USER_PROFILE_DATABASE)

    @Bean
    fun skillUserProfileRepository(
        couchDbClient: CouchDbClient,
        objectMapper: ObjectMapper
    ): SkillUserProfileRepository =
        CouchDbSkillUserProfileRepository(
            couchDbClient = couchDbClient,
            objectMapper = objectMapper
        )

    @Bean
    fun skillLabFetchUserProfileUpdatedUseCase(
        skillLabClient: SkillLabClient,
        skillUserProfileRepository: SkillUserProfileRepository,
        userProfileUpdatePublisher: UserProfileUpdatePublisher
    ): FetchUserProfileUpdatesUseCase =
        SkillLabFetchUserProfileUpdatesUseCase(
            skillLabClient = skillLabClient,
            skillUserProfileRepository = skillUserProfileRepository,
            userProfileUpdatePublisher = userProfileUpdatePublisher
        )

    @Bean
    fun skillLabSyncUserProfileUseCase(
        skillLabClient: SkillLabClient,
        skillUserProfileRepository: SkillUserProfileRepository,
        objectMapper: ObjectMapper
    ): SyncUserProfileUseCase =
        SkillLabSyncUserProfileUseCase(
            skillLabClient = skillLabClient,
            skillUserProfileRepository = skillUserProfileRepository,
            objectMapper = objectMapper
        )

    @Bean
    fun searchUserProfileUseCase(
        skillUserProfileRepository: SkillUserProfileRepository,
        objectMapper: ObjectMapper
    ): SearchUserProfileUseCase =
        InMemorySearchUserProfileUseCase(
            userProfileRepository = skillUserProfileRepository,
            objectMapper = objectMapper
        )
}
