package com.aamdigital.aambackendservice.queue.core

import com.aamdigital.aambackendservice.error.AamErrorCode
import com.aamdigital.aambackendservice.error.InvalidArgumentException
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

class DefaultQueueMessageParser(
    private val objectMapper: ObjectMapper,
) : QueueMessageParser {

    enum class DefaultQueueMessageParserErrorCode : AamErrorCode {
        INVALID_JSON,
        MISSING_TYPE_FIELD,
        INVALID_CLASS_NAME,
        MISSING_PAYLOAD_FIELD,
        INVALID_PAYLOAD
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val TYPE_FIELD = "eventType"
        private const val PAYLOAD_FIELD = "event"
    }

    private fun getJsonNode(body: ByteArray): JsonNode {
        return try {
            objectMapper.readTree(body)
        } catch (ex: JacksonException) {
            throw InvalidArgumentException(
                message = "Could not parse message.",
                code = DefaultQueueMessageParserErrorCode.INVALID_JSON,
                cause = ex,
            )
        }
    }

    @Throws(InvalidArgumentException::class)
    override fun getType(body: ByteArray): String {
        val jsonNode = getJsonNode(body)

        if (!jsonNode.has(TYPE_FIELD)) {
            throw InvalidArgumentException(
                message = "Could not extract type from message.",
                code = DefaultQueueMessageParserErrorCode.MISSING_TYPE_FIELD,
            )
        }

        return jsonNode.get(TYPE_FIELD).textValue()
    }

    @Throws(InvalidArgumentException::class)
    override fun getTypeKClass(body: ByteArray): KClass<*> {
        val typeString = getType(body)

        try {
            return Class.forName(typeString).kotlin
        } catch (ex: ClassNotFoundException) {
            logger.debug("INVALID_CLASS_NAME_DEBUG_EX", ex)
            throw InvalidArgumentException(
                message = "Could not find Class for this type.",
                code = DefaultQueueMessageParserErrorCode.INVALID_CLASS_NAME,
            )
        }
    }

    @Throws(InvalidArgumentException::class)
    override fun <T : Any> getPayload(body: ByteArray, kClass: KClass<T>): T {
        val jsonNode = getJsonNode(body)

        if (!jsonNode.has(PAYLOAD_FIELD)) {
            throw InvalidArgumentException(
                message = "Could not extract payload from message.",
                code = DefaultQueueMessageParserErrorCode.MISSING_PAYLOAD_FIELD,
            )
        }

        return try {
            objectMapper.treeToValue(jsonNode.get(PAYLOAD_FIELD), kClass.java)
        } catch (ex: JacksonException) {
            throw InvalidArgumentException(
                message = "Could not parse payload object from message.",
                code = DefaultQueueMessageParserErrorCode.INVALID_PAYLOAD,
                cause = ex,
            )
        }
    }
}
