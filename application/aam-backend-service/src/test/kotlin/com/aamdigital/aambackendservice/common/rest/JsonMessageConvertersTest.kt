package com.aamdigital.aambackendservice.common.rest

import com.aamdigital.aambackendservice.notification.domain.NotificationType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration
import org.springframework.core.io.InputStreamResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.util.ClassUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import org.springframework.web.context.WebApplicationContext

/**
 * Pins how the application converts HTTP bodies until it moves to Jackson 3: JSON goes through
 * Jackson 2 and the shared ObjectMapper only, and the JSON converter keeps the place Spring Boot
 * gives it, after the converters that pass String and Resource bodies through as they are.
 */
class JsonMessageConvertersTest {
    private lateinit var mockWebServer: MockWebServer

    // Jackson2AutoConfiguration is deprecated along with the rest of Spring Boot's Jackson 2 support
    @Suppress("DEPRECATION")
    private val contextRunner =
        WebApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    Jackson2AutoConfiguration::class.java,
                    HttpMessageConvertersAutoConfiguration::class.java,
                    WebMvcAutoConfiguration::class.java,
                    RestClientAutoConfiguration::class.java
                )
            ).withUserConfiguration(ObjectMapperConfiguration::class.java, BodiesController::class.java)

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `should keep Jackson 3 off the classpath so that no default converter picks it`() {
        // When
        val jackson3Present = ClassUtils.isPresent("tools.jackson.databind.ObjectMapper", javaClass.classLoader)

        // Then
        assertThat(jackson3Present).isFalse()
    }

    @Test
    fun `should read JSON request bodies with the settings of the shared ObjectMapper`() {
        contextRunner.run { context ->
            // Given
            val mockMvc = mockMvc(context)

            // When
            val result =
                mockMvc
                    .perform(
                        post("/bodies/notification-type")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"type":"a_type_added_later"}""")
                    ).andReturn()

            // Then
            assertThat(result.response.status).isEqualTo(200)
            assertThat(result.response.contentAsString).isEqualTo("""{"type":"UNKNOWN"}""")
        }
    }

    @Test
    fun `should answer a String body as it is rather than as a JSON string`() {
        contextRunner.run { context ->
            // Given
            val mockMvc = mockMvc(context)

            // When
            val result = mockMvc.perform(get("/bodies/text")).andReturn()

            // Then
            assertThat(result.response.contentAsString).isEqualTo("plain text")
            assertThat(result.response.contentType).startsWith(MediaType.TEXT_PLAIN_VALUE)
        }
    }

    @Test
    fun `should stream a Resource body verbatim even when it is declared as JSON`() {
        contextRunner.run { context ->
            // Given
            val mockMvc = mockMvc(context)

            // When
            val result = mockMvc.perform(get("/bodies/json-stream")).andReturn()

            // Then
            assertThat(result.response.contentAsString).isEqualTo(BodiesController.STREAMED_JSON)
            assertThat(result.response.contentType).startsWith(MediaType.APPLICATION_JSON_VALUE)
        }
    }

    @Test
    fun `should read a JSON reply as a raw String through the RestClient builder of Spring Boot`() {
        contextRunner.run { context ->
            // Given
            val couchDbReply = """{"ok":true,"id":"ReportCalculation:1","rev":"2-abc"}"""
            mockWebServer.enqueue(
                MockResponse()
                    .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .setBody(couchDbReply)
            )
            val restClient =
                context
                    .getBean(RestClient.Builder::class.java)
                    .baseUrl(mockWebServer.url("/").toString())
                    .build()

            // When
            val body =
                restClient
                    .put()
                    .uri("/app/ReportCalculation:1/data.json")
                    .retrieve()
                    .body(String::class.java)

            // Then
            assertThat(body).isEqualTo(couchDbReply)
        }
    }

    private fun mockMvc(context: WebApplicationContext): MockMvc = MockMvcBuilders.webAppContextSetup(context).build()

    data class NotificationTypeBody(
        val type: NotificationType
    )

    @RestController
    class BodiesController {
        companion object {
            const val STREAMED_JSON = """{"data":[{"name":"streamed as it is"}]}"""
        }

        @PostMapping("/bodies/notification-type")
        fun notificationType(
            @RequestBody body: NotificationTypeBody
        ): NotificationTypeBody = body

        @GetMapping("/bodies/text")
        fun text(): String = "plain text"

        @GetMapping("/bodies/json-stream", produces = [MediaType.APPLICATION_JSON_VALUE])
        fun jsonStream(): ResponseEntity<InputStreamResource> =
            ResponseEntity.ok().body(InputStreamResource(STREAMED_JSON.byteInputStream()))
    }
}
