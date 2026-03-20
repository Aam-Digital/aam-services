package com.aamdigital.aambackendservice.common.changes.di

import com.aamdigital.aambackendservice.common.changes.core.ChangeEventPublisher
import com.aamdigital.aambackendservice.common.changes.queue.DefaultChangeEventPublisher
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.core.FanoutExchange
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ChangesQueueConfiguration {
    companion object {
        const val DOCUMENT_CHANGES_EXCHANGE = "document.changes"
    }

    @Bean("document-changes-exchange")
    fun documentChangesExchange(): FanoutExchange = FanoutExchange(DOCUMENT_CHANGES_EXCHANGE)

    @Bean
    fun defaultChangeEventPublisher(
        objectMapper: ObjectMapper,
        rabbitTemplate: RabbitTemplate
    ): ChangeEventPublisher =
        DefaultChangeEventPublisher(
            objectMapper = objectMapper,
            rabbitTemplate = rabbitTemplate
        )
}
