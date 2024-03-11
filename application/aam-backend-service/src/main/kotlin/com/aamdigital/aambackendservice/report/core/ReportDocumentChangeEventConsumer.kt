package com.aamdigital.aambackendservice.report.core

import com.rabbitmq.client.Channel
import org.springframework.amqp.core.Message
import reactor.core.publisher.Mono

interface ReportDocumentChangeEventConsumer {
    fun consume(rawMessage: String, messsage: Message, channel: Channel): Mono<Unit>
}
