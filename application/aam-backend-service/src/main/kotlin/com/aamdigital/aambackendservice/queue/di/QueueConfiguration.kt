package com.aamdigital.aambackendservice.queue.di

import com.aamdigital.aambackendservice.queue.core.DefaultQueueMessageParser
import com.aamdigital.aambackendservice.queue.core.QueueMessageParser
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class QueueConfiguration {
    @Bean
    fun defaultQueueMessageParser(objectMapper: ObjectMapper): QueueMessageParser = DefaultQueueMessageParser(
        objectMapper = objectMapper
    )

    @Bean
    fun defaultMessageConverter(): MessageConverter = Jackson2JsonMessageConverter()
}
