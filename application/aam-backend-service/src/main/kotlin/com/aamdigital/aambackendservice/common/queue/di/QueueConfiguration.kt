package com.aamdigital.aambackendservice.common.queue.di

import com.aamdigital.aambackendservice.common.queue.core.DefaultQueueMessageParser
import com.aamdigital.aambackendservice.common.queue.core.QueueMessageParser
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateConfigurer
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class QueueConfiguration {

    @Bean
    @Primary
    fun rabbitTemplate(
        configurer: RabbitTemplateConfigurer,
        connectionFactory: ConnectionFactory,
        customizers: ObjectProvider<RabbitTemplateCustomizer>
    ): RabbitTemplate {
        val template = RabbitTemplate()
        configurer.configure(template, connectionFactory)
        customizers.orderedStream().forEach { customizer: RabbitTemplateCustomizer ->
            customizer.customize(template)
        }
        template.setObservationEnabled(true)
        return template
    }

    @Bean
    fun defaultQueueMessageParser(objectMapper: ObjectMapper): QueueMessageParser = DefaultQueueMessageParser(
        objectMapper = objectMapper
    )

    @Bean
    fun defaultMessageConverter(): MessageConverter = Jackson2JsonMessageConverter()
}
