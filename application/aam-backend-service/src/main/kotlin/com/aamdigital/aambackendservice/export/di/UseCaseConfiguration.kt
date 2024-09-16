package com.aamdigital.aambackendservice.export.di

import com.aamdigital.aambackendservice.couchdb.core.CouchDbClient
import com.aamdigital.aambackendservice.export.core.CreateTemplateUseCase
import com.aamdigital.aambackendservice.export.core.FetchTemplateUseCase
import com.aamdigital.aambackendservice.export.core.RenderTemplateUseCase
import com.aamdigital.aambackendservice.export.core.TemplateStorage
import com.aamdigital.aambackendservice.export.storage.DefaultTemplateStorage
import com.aamdigital.aambackendservice.export.usecase.DefaultCreateTemplateUseCase
import com.aamdigital.aambackendservice.export.usecase.DefaultFetchTemplateUseCase
import com.aamdigital.aambackendservice.export.usecase.DefaultRenderTemplateUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class UseCaseConfiguration {
    @Bean(name = ["default-template-storage"])
    fun defaultTemplateStorage(
        couchDbClient: CouchDbClient,
    ): TemplateStorage = DefaultTemplateStorage(couchDbClient)

    @Bean(name = ["default-create-template-use-case"])
    fun defaultCreateTemplateUseCase(
        @Qualifier("aam-render-api-client") webClient: WebClient,
        objectMapper: ObjectMapper
    ): CreateTemplateUseCase = DefaultCreateTemplateUseCase(webClient, objectMapper)


    @Bean(name = ["default-fetch-template-use-case"])
    fun defaultFetchTemplateUseCase(
        @Qualifier("aam-render-api-client") webClient: WebClient,
        templateStorage: TemplateStorage
    ): FetchTemplateUseCase = DefaultFetchTemplateUseCase(webClient, templateStorage)

    @Bean(name = ["default-render-template-use-case"])
    fun defaultRenderTemplateUseCase(
        @Qualifier("aam-render-api-client") webClient: WebClient,
        objectMapper: ObjectMapper,
        templateStorage: TemplateStorage
    ): RenderTemplateUseCase = DefaultRenderTemplateUseCase(webClient, objectMapper, templateStorage)
}
