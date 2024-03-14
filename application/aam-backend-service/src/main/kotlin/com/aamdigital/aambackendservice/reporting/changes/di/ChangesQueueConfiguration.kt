package com.aamdigital.aambackendservice.reporting.changes.di

import com.aamdigital.aambackendservice.queue.core.QueueMessageParser
import com.aamdigital.aambackendservice.reporting.changes.core.ChangeEventPublisher
import com.aamdigital.aambackendservice.reporting.changes.core.CreateDocumentChangeUseCase
import com.aamdigital.aambackendservice.reporting.changes.core.DatabaseChangeEventConsumer
import com.aamdigital.aambackendservice.reporting.changes.queue.DefaultChangeEventPublisher
import com.aamdigital.aambackendservice.reporting.changes.queue.DefaultDatabaseChangeEventConsumer
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.FanoutExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ChangesQueueConfiguration {
    companion object {
        const val DB_CHANGES_QUEUE = "db.changes"
        const val DB_CHANGES_DEAD_LETTER_QUEUE = "db.changes.deadLetter"
        const val DB_CHANGES_DEAD_LETTER_EXCHANGE = "$DB_CHANGES_QUEUE.dlx"
        const val DOCUMENT_CHANGES_EXCHANGE = "document.changes"
    }

    @Bean
    fun dbChangesQueue(): Queue = QueueBuilder
        .durable(DB_CHANGES_QUEUE)
        .deadLetterExchange(DB_CHANGES_DEAD_LETTER_EXCHANGE)
        .build()

    @Bean("db-changes-dead-letter-queue")
    fun dbChangesDeadLetterQueue(): Queue = QueueBuilder
        .durable(DB_CHANGES_DEAD_LETTER_QUEUE)
        .build()

    @Bean("db-changes-dead-letter-exchange")
    fun dbChangesDeadLetterExchange(): FanoutExchange = FanoutExchange(DB_CHANGES_DEAD_LETTER_EXCHANGE)

    @Bean
    fun deadLetterBinding(
        @Qualifier("db-changes-dead-letter-queue") dbChangesDeadLetterQueue: Queue,
        @Qualifier("db-changes-dead-letter-exchange") dbChangesDeadLetterExchange: FanoutExchange,
    ): Binding =
        BindingBuilder.bind(dbChangesDeadLetterQueue).to(dbChangesDeadLetterExchange)

    @Bean("document-changes-exchange")
    fun documentChangesExchange(): FanoutExchange = FanoutExchange(DOCUMENT_CHANGES_EXCHANGE)

    @Bean
    fun defaultChangeEventPublisher(
        objectMapper: ObjectMapper,
        rabbitTemplate: RabbitTemplate,
    ): ChangeEventPublisher = DefaultChangeEventPublisher(
        objectMapper = objectMapper,
        rabbitTemplate = rabbitTemplate,
    )

    @Bean
    fun defaultDocumentChangeEventProcessor(
        messageParser: QueueMessageParser,
        useCase: CreateDocumentChangeUseCase,
    ): DatabaseChangeEventConsumer = DefaultDatabaseChangeEventConsumer(
        messageParser = messageParser,
        useCase = useCase,
    )
}
