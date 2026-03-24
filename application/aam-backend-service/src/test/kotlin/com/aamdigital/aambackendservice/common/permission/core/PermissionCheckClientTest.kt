package com.aamdigital.aambackendservice.common.permission.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.web.client.RestClient

@ExtendWith(MockitoExtension::class)
class PermissionCheckClientTest {
    @Mock
    lateinit var restClient: RestClient

    @Mock
    lateinit var requestBodyUriSpec: RestClient.RequestBodyUriSpec

    @Mock
    lateinit var responseSpec: RestClient.ResponseSpec

    @Test
    fun `should allow all when client is not configured`() {
        val client = PermissionCheckClient()

        val result =
            client.checkPermissions(
                userIds = listOf("user-1", "user-2"),
                entityDoc = mapOf("_id" to "Child:1"),
                action = "read"
            )

        assertThat(result).isEqualTo(mapOf("user-1" to true, "user-2" to true))
    }

    @Test
    fun `should return empty map when request fails`() {
        whenever(restClient.post()).thenReturn(requestBodyUriSpec)
        whenever(requestBodyUriSpec.uri(any<String>())).thenReturn(requestBodyUriSpec)
        whenever(requestBodyUriSpec.body(any<Any>())).thenReturn(requestBodyUriSpec)
        whenever(requestBodyUriSpec.retrieve()).thenReturn(responseSpec)
        whenever(responseSpec.body(Map::class.java)).thenThrow(RuntimeException("connection failure"))

        val client = PermissionCheckClient(restClient)

        val result =
            client.checkPermissions(
                userIds = listOf("user-1"),
                entityDoc = mapOf("_id" to "Child:1"),
                action = "read"
            )

        assertThat(result).isEmpty()
    }
}
