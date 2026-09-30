package com.aamdigital.aambackendservice.common.rest

import com.aamdigital.aambackendservice.common.auth.core.AuthConfig
import com.aamdigital.aambackendservice.common.auth.core.AuthProvider
import com.aamdigital.aambackendservice.common.auth.core.KeycloakAuthProvider
import com.aamdigital.aambackendservice.common.auth.core.TokenResponse
import com.aamdigital.aambackendservice.common.auth.di.AuthConfiguration
import com.aamdigital.aambackendservice.common.couchdb.dto.DocSuccess
import com.aamdigital.aambackendservice.common.permission.di.PermissionConfiguration
import com.aamdigital.aambackendservice.common.permission.di.ReplicationBackendClientConfiguration
import com.aamdigital.aambackendservice.export.core.RenderTemplateError
import com.aamdigital.aambackendservice.export.di.AamRenderApiClientConfiguration
import com.aamdigital.aambackendservice.export.di.AamRenderApiConfiguration
import com.aamdigital.aambackendservice.export.usecase.CarboneRenderApiClient
import com.aamdigital.aambackendservice.notification.core.config.NotificationRuleDto
import com.aamdigital.aambackendservice.notification.domain.NotificationType
import com.aamdigital.aambackendservice.reporting.report.di.SqsClientConfiguration
import com.aamdigital.aambackendservice.reporting.report.di.SqsConfiguration
import com.aamdigital.aambackendservice.reporting.report.sqs.QueryRequest
import com.aamdigital.aambackendservice.reporting.report.sqs.SqsQueryStorage
import com.aamdigital.aambackendservice.reporting.report.sqs.SqsSchemaService
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import java.time.Instant

/**
 * Pins the JSON on the wire where the application sends and receives it over HTTP: Spring MVC,
 * Spring Boot's `RestClient.Builder`, and the clients the application builds itself. Each of them
 * has to use the shared mapper; a converter falling back to a default mapper of its own writes
 * dates, characters and enums differently and reads less leniently.
 *
 * The context loads `application.yaml`, so its `spring.jackson.*` settings apply as in production.
 * The expected values were recorded from the Jackson 2 mapper the application used before it moved
 * to Jackson 3.
 */
class JsonHttpWireFormatTest {
    companion object {
        private val INSTANT: Instant = Instant.parse("2026-09-28T10:15:30.123456789Z")
        private val SAMPLE =
            mapOf(
                "title" to "Ada \uD83D\uDE00 </script>",
                "at" to INSTANT,
                "type" to NotificationType.ENTITY_CHANGE,
                "nothing" to null
            )
        private const val LENIENT_RULE =
            """{"label":"Rule","notificationType":"a_type_added_later","entityType":"Child",
            "changeType":["created",null],"conditions":{"name":"Ada"},"addedLater":1}
            """
    }

    private lateinit var mockWebServer: MockWebServer

    // Jackson2AutoConfiguration is deprecated along with the rest of Spring Boot's Jackson 2 support
    @Suppress("DEPRECATION")
    private val contextRunner =
        WebApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withConfiguration(
                AutoConfigurations.of(
                    Jackson2AutoConfiguration::class.java,
                    HttpMessageConvertersAutoConfiguration::class.java,
                    WebMvcAutoConfiguration::class.java,
                    RestClientAutoConfiguration::class.java
                )
            ).withUserConfiguration(ObjectMapperConfiguration::class.java, WireController::class.java)

    private val mapper = applicationJsonMapper()

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
    fun `should answer API clients with the JSON the shared mapper writes`() {
        contextRunner.run { context ->
            // Given
            val mockMvc = MockMvcBuilders.webAppContextSetup(context).build()

            // When
            val result = mockMvc.perform(get("/wire/sample")).andReturn()

            // Then
            assertThat(String(result.response.contentAsByteArray, Charsets.UTF_8)).isEqualTo(
                """{"title":"Ada \uD83D\uDE00 </script>","at":1790590530.123456789,"type":"entity_change","nothing":null}"""
            )
        }
    }

    @Test
    fun `should read API request bodies as leniently as the shared mapper does`() {
        contextRunner.run { context ->
            // Given
            val mockMvc = MockMvcBuilders.webAppContextSetup(context).build()

            // When
            val result =
                mockMvc
                    .perform(post("/wire/rule").contentType(MediaType.APPLICATION_JSON).content(LENIENT_RULE))
                    .andReturn()

            // Then
            assertThat(result.response.contentAsString).isEqualTo(
                """NotificationRuleDto(label=Rule, notificationType=UNKNOWN, entityType=Child, changeType=[created, null], conditions={"name":"Ada"}, enabled=false)"""
            )
        }
    }

    @Test
    fun `should send and read JSON through the RestClient builder of Spring Boot with the shared mapper`() {
        contextRunner.run { context ->
            // Given
            mockWebServer.enqueue(
                MockResponse()
                    .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .setBody("""{"ok":true,"id":"Child:1","rev":"2-abc","addedLater":true}""")
            )
            val restClient =
                context
                    .getBean(RestClient.Builder::class.java)
                    .baseUrl(mockWebServer.url("/").toString())
                    .build()

            // When
            val reply =
                restClient
                    .put()
                    .uri("/app/Child:1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(SAMPLE)
                    .retrieve()
                    .body(DocSuccess::class.java)

            // Then
            assertThat(mockWebServer.takeRequest().body.readUtf8()).isEqualTo(
                """{"title":"Ada \uD83D\uDE00 </script>","at":1790590530.123456789,"type":"entity_change","nothing":null}"""
            )
            assertThat(reply.toString()).isEqualTo("DocSuccess(ok=true, id=Child:1, rev=2-abc)")
        }
    }

    @Test
    fun `should send and read permission checks with the shared mapper`() {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"user-1":{"permitted":true},"user-2":{"permitted":false,"reason":"x"},"user-3":{}}""")
        )
        val client =
            PermissionConfiguration().permissionCheckClient(
                ReplicationBackendClientConfiguration(basePath = mockWebServer.url("/").toString())
            )

        // When
        val permissions = client.checkPermissions(userIds = listOf("user-1", "user-2", "user-3"), entityId = "Child:1")

        // Then
        assertThat(mockWebServer.takeRequest().body.readUtf8()).isEqualTo(
            """{"userIds":["user-1","user-2","user-3"],"entityId":"Child:1","action":"read"}"""
        )
        assertThat(permissions.toString()).isEqualTo("{user-1=true, user-2=false, user-3=false}")
    }

    @Test
    fun `should send report queries to SQS with the shared mapper`() {
        // Given
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val schemaService = mock<SqsSchemaService>()
        whenever(schemaService.getSchemaPath()).thenReturn("/app/_design/sqlite:config")
        val storage =
            SqsQueryStorage(
                sqsClient =
                    SqsConfiguration().sqsRestClient(
                        SqsClientConfiguration(
                            basePath = mockWebServer.url("/").toString(),
                            basicAuthUsername = "user",
                            basicAuthPassword = "password"
                        )
                    ),
                schemaService = schemaService
            )

        // When
        storage
            .executeQuery(
                QueryRequest(query = "SELECT * FROM Child WHERE name = ?", args = listOf("Ada \uD83D\uDE00")),
                "Report:1"
            ).close()

        // Then
        assertThat(mockWebServer.takeRequest().body.readUtf8()).isEqualTo(
            """{"query":"SELECT * FROM Child WHERE name = ?","args":["Ada \uD83D\uDE00"]}"""
        )
    }

    @Test
    fun `should send render requests to the template engine with the shared mapper`() {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"success":true,"data":{"renderId":"render-1"}}""")
        )
        val renderClient =
            CarboneRenderApiClient(
                renderClient =
                    AamRenderApiConfiguration().aamRenderApiClient(
                        authProvider =
                            object : AuthProvider {
                                override fun fetchToken(authClientConfig: AuthConfig): TokenResponse =
                                    TokenResponse("token")
                            },
                        configuration = AamRenderApiClientConfiguration(basePath = mockWebServer.url("/").toString())
                    ),
                objectMapper = mapper,
                templateStorage = mock(),
                notFoundCode = RenderTemplateError.NOT_FOUND_ERROR,
                fetchTemplateFailedCode = RenderTemplateError.FETCH_TEMPLATE_FAILED_ERROR,
                createRenderRequestFailedCode = RenderTemplateError.CREATE_RENDER_REQUEST_FAILED_ERROR,
                fetchRenderResultFailedCode = RenderTemplateError.FETCH_RENDER_ID_REQUEST_FAILED_ERROR,
                parseResponseCode = RenderTemplateError.PARSE_RESPONSE_ERROR
            )
        val bodyData =
            mapper.readTree(
                """{"data":{"name":"Ada \uD83D\uDE00","age":3.50,"tags":["a",null],"school":{}},"convertTo":"pdf"}"""
            )

        // When
        val raw = renderClient.createRenderRequest(templateId = "template-1", bodyData = bodyData)

        // Then
        assertThat(mockWebServer.takeRequest().body.readUtf8()).isEqualTo(
            """{"data":{"name":"Ada \uD83D\uDE00","age":3.5,"tags":["a",null],"school":{}},"convertTo":"pdf"}"""
        )
        assertThat(renderClient.parseRenderId(raw)).isEqualTo("render-1")
    }

    @Test
    fun `should post the token request as a form and read the token from the text reply`() {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("""{"access_token":"token-1","expires_in":300}""")
        )
        val authProvider =
            KeycloakAuthProvider(httpClient = AuthConfiguration().aamKeycloakRestClient(), objectMapper = mapper)

        // When
        val token =
            authProvider.fetchToken(
                AuthConfig(
                    clientId = "client",
                    clientSecret = "secret",
                    tokenEndpoint = mockWebServer.url("/token").toString(),
                    grantType = "client_credentials",
                    scope = ""
                )
            )

        // Then
        val request = mockWebServer.takeRequest()
        assertThat(request.getHeader("Content-Type")).startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        assertThat(
            request.body.readUtf8()
        ).isEqualTo("client_id=client&client_secret=secret&grant_type=client_credentials")
        assertThat(token.token).isEqualTo("token-1")
    }

    @RestController
    class WireController {
        @GetMapping("/wire/sample")
        fun sample(): Map<String, Any?> = SAMPLE

        @PostMapping("/wire/rule", produces = [MediaType.TEXT_PLAIN_VALUE])
        fun rule(
            @RequestBody rule: NotificationRuleDto
        ): String = rule.toString()
    }
}
