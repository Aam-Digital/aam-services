package com.aamdigital.aambackendservice.reporting.report.core

import com.rabbitmq.client.Channel
import org.springframework.amqp.core.Message

interface ReportDocumentChangeEventConsumer {
    fun consume(rawMessage: String, message: Message, channel: Channel)
}
