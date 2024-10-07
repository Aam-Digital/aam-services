package com.aamdigital.aambackendservice.http

import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.error.NetworkException
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.timeout.ReadTimeoutHandler
import java.util.concurrent.TimeUnit

class AamReadTimeoutHandler(
    private val timeout: Long = 30,
    private val timeUnit: TimeUnit = TimeUnit.SECONDS,
) : ReadTimeoutHandler(timeout, timeUnit) {
    private var closed = false

    @Throws(AamException::class)
    override fun readTimedOut(ctx: ChannelHandlerContext) {
        if (!this.closed) {
            ctx.fireExceptionCaught(
                NetworkException(
                    message = "The connection has not responded within $timeout ${timeUnit.toString().lowercase()}",
                    code = "READ_TIMEOUT_EXCEPTION",
                )
            )
            ctx.close()
            this.closed = true
        }
    }
}
