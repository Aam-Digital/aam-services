package com.aamdigital.aambackendservice.common.rest

import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import java.time.Duration

/**
 * A request factory that bounds both connecting and waiting for the response by [timeout], so a
 * `RestClient` built with it cannot block its caller indefinitely on an unresponsive server.
 */
fun requestFactoryWithTimeout(timeout: Duration): ClientHttpRequestFactory =
    SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(timeout)
        setReadTimeout(timeout)
    }
