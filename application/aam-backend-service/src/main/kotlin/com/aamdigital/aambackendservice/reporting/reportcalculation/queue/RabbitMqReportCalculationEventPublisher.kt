package com.aamdigital.aambackendservice.reporting.reportcalculation.queue

import com.aamdigital.aambackendservice.error.AamErrorCode
import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.error.InternalServerException
import com.aamdigital.aambackendservice.reporting.domain.event.ReportCalculationEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpException
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service
import java.util.*

// todo conditional bean creation
@Service
class RabbitMqReportCalculationEventPublisher(
    private val objectMapper: ObjectMapper,
    private val rabbitTemplate: RabbitTemplate,
) {

    enum class RabbitMqReportCalculationEventErrorCode : AamErrorCode {
        EVENT_PUBLISH_ERROR
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    @Throws(AamException::class)
    fun publish(channel: String, event: ReportCalculationEvent) {
        try {
            rabbitTemplate.send(
                channel,
                rabbitTemplate.messageConverter.toMessage(event, MessageProperties())
            )
        } catch (ex: AmqpException) {
            throw InternalServerException(
                message = "Could not publish ReportCalculationEvent: $event",
                code = RabbitMqReportCalculationEventErrorCode.EVENT_PUBLISH_ERROR,
                cause = ex
            )
        }
        return
    }
}
