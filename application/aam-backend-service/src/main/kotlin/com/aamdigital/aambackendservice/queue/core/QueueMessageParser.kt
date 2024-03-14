package com.aamdigital.aambackendservice.queue.core

import kotlin.reflect.KClass

interface QueueMessageParser {
    fun getType(body: ByteArray): String
    fun getTypeKClass(body: ByteArray): KClass<*>
    fun <T : Any> getPayload(body: ByteArray, kClass: KClass<T>): T
}
