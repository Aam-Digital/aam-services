package com.aamdigital.aambackendservice.notification.controller

import com.aamdigital.aambackendservice.common.error.HttpErrorDto
import com.aamdigital.aambackendservice.notification.repository.UserDeviceEntity
import com.aamdigital.aambackendservice.notification.repository.UserDeviceRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.util.Optional

class NotificationDeviceControllerTest {
    private val userDeviceRepository = mock<UserDeviceRepository>()

    private lateinit var controller: NotificationDeviceController

    private val registration = DeviceRegistrationDto(deviceName = "phone", deviceToken = DEVICE_TOKEN)

    companion object {
        private const val DEVICE_TOKEN = "device-token-1"
    }

    @BeforeEach
    fun setUp() {
        controller = NotificationDeviceController(userDeviceRepository = userDeviceRepository)
    }

    private fun authentication(configure: Jwt.Builder.() -> Unit): JwtAuthenticationToken =
        JwtAuthenticationToken(
            Jwt
                .withTokenValue("token")
                .header("alg", "none")
                .claim("azp", "app")
                .apply(configure)
                .build()
        )

    private fun registeredDevice(userIdentifier: String) =
        whenever(userDeviceRepository.findByDeviceToken(DEVICE_TOKEN)).thenReturn(
            Optional.of(
                UserDeviceEntity(deviceName = "phone", deviceToken = DEVICE_TOKEN, userIdentifier = userIdentifier)
            )
        )

    @Test
    fun `should register the device for the subject of the token`() {
        // Given
        val authentication = authentication { subject("user-1") }

        // When
        val response = controller.registerDevice(registration, authentication)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        verify(userDeviceRepository).save(
            UserDeviceEntity(deviceName = "phone", deviceToken = DEVICE_TOKEN, userIdentifier = "user-1")
        )
    }

    @Test
    fun `should reject a device registration from a token without subject`() {
        // Given
        val authentication = authentication { claim("username", "user-1") }

        // When
        val response = controller.registerDevice(registration, authentication)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat((response.body as HttpErrorDto).errorMessage).isEqualTo("No subject found in the token.")
        verify(userDeviceRepository, never()).save(any())
    }

    @Test
    fun `should identify a caller without subject by the username claim`() {
        // Given
        registeredDevice(userIdentifier = "user-1")
        val authentication = authentication { claim("username", "user-1") }

        // When
        val response = controller.getDeviceRegistration(DEVICE_TOKEN, authentication)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun `should not let a caller without subject unregister the device of another user`() {
        // Given
        registeredDevice(userIdentifier = "user-1")
        val authentication = authentication { claim("username", "other-user") }

        // When
        val response = controller.unregisterDevice(DEVICE_TOKEN, authentication)

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        verify(userDeviceRepository, never()).deleteByDeviceToken(any())
    }
}
