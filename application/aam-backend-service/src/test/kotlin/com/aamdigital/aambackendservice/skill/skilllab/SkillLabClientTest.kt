package com.aamdigital.aambackendservice.skill.skilllab

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.ExternalSystemException
import com.aamdigital.aambackendservice.rest.ObjectMapperConfiguration
import com.aamdigital.aambackendservice.skill.di.SkillConfigurationSkillLab
import com.aamdigital.aambackendservice.skill.di.SkillLabApiClientConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.Pageable
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(MockitoExtension::class)
class SkillLabClientTest {
    companion object {
        const val WEBSERVER_PORT = 6000
        lateinit var mockWebServer: MockWebServer
        lateinit var objectMapper: ObjectMapper


        @JvmStatic
        @BeforeAll
        fun init() {
            mockWebServer = MockWebServer()
            objectMapper = ObjectMapperConfiguration().objectMapper()
        }
    }

    private lateinit var service: SkillLabClient

    @BeforeEach
    fun beforeEach() {
        startWebserver()
        setUp()
    }

    @AfterEach
    fun afterEach() {
        stopWebserver()
    }

    @Test
    fun `fetchUserProfile() should throw ExternalSystemException when external api returns invalid response`() {
        // given
        mockWebServer.enqueue(
            MockResponse()
        )

        // then
        assertFailsWith(
            exceptionClass = ExternalSystemException::class,
            message = "Empty or invalid response from server",
            block = {
                // when
                service.fetchUserProfile(DomainReference("user-profile-1"))
            }
        )
    }

    @Test
    fun `fetchUserProfile() should parse valid 2xx response to SkillLabProfileResponseDto`() {
        // given
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "profile": {
                    "id": "user-profile-1",
                    "city": "example-city",
                    "beneficiary_role": "example-beneficiary-role",
                    "country": "example-country",
                    "arrival_in_country": null,
                    "nationality": "example-nationality",
                    "gender": "male",
                    "gender_custom": "",
                    "languages": [
                      {
                        "language": "ar",
                        "proficiency": "Fluent",
                        "assessment_level": null
                      }
                    ],
                    "updated_at": "2024-09-26T18:58:50.271Z",
                    "projects": [
                      "example-project-1"
                    ],
                    "mobile_number": "+00000000",
                    "birthday": "2000-01-01",
                    "full_name": "Max Muster",
                    "email": "example@local.org",
                    "experiences": [
                      {
                        "id": "00000000-0000-0000-0000-000000001230",
                        "title": "Waiter",
                        "category": "job",
                        "start_date": "2005-01-01",
                        "end_date": null,
                        "city": "Berlin",
                        "country": "example-country",
                        "duration_per_week": "full_time",
                        "experiences_skills": [
                          {
                            "id": "cbb66960-b9f4-42d6-9a18-ce6a98bca073",
                            "external_id": "http://data.europa.eu/esco/skill/1e9bd245-3118-45e0-83e4-751f2ce556e5",
                            "choice": "always"
                          },
                          {
                            "id": "a6e3e168-8410-434d-b1d1-8de056ed1e75",
                            "external_id": "http://data.europa.eu/esco/skill/2239694b-771a-4586-8b54-2794e361a9ae",
                            "choice": "always"
                          }
                        ],
                        "organisation": "example-organisation"
                      }
                    ],
                    "street_and_house_number": "Example street 123",
                    "sessions": [],
                    "avatar_url": null
                  }
                }
                """.trimIndent()
            )
        )

        // when
        val response = service.fetchUserProfile(DomainReference("user-profile-1"))

        // then
        assertThat(response).isInstanceOf(SkillLabProfileResponseDto::class.java)
        assertEquals("user-profile-1", response.profile.id)
    }

    @Test
    fun `fetchUserProfile() should parse 4xx response to SkillLabErrorResponseDto`() {
        // given
        mockWebServer.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """
                {
                  "error": {
                    "code": 400,
                    "title": "example-exception-title",
                    "message": "example-exception-message",
                    "detail": "example-exception-detail"
                  }
                }
                """.trimIndent()
            )
        )

        // then
        assertFailsWith(
            exceptionClass = ExternalSystemException::class,
            message = "example-exception-message",
            block = {
                // when
                service.fetchUserProfile(DomainReference("user-profile-1"))
            }
        )
    }

    @Test
    fun `fetchUserProfiles() should throw ExternalSystemException when external api returns invalid response`() {
        // given
        mockWebServer.enqueue(
            MockResponse()
        )

        // then
        assertFailsWith(
            exceptionClass = ExternalSystemException::class,
            message = "Empty or invalid response from server",
            block = {
                // when
                service.fetchUserProfiles(pageable = Pageable.ofSize(10), updatedFrom = null)
            }
        )
    }

    @Test
    fun `fetchUserProfiles() should parse valid 2xx response to SkillLabProfilesResponseDto`() {
        // given
        mockWebServer.enqueue(
            MockResponse().setBody(
                """
                {
                  "pagination": {
                    "currentPage": 1,
                    "pageSize": 10,
                    "totalPages": 1,
                    "totalElements": 2
                  },
                  "results": [
                    {
                      "id": "00000000-0000-0000-0000-000000001230"
                    },
                    {
                      "id": "00000000-0000-0000-0000-000000004560"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        // when
        val response = service.fetchUserProfiles(
            pageable = Pageable.ofSize(10),
            updatedFrom = " "
        )

        // then
        assertThat(response).isInstanceOf(List::class.java)
        assertEquals("00000000-0000-0000-0000-000000001230", response.first().id)
        assertEquals("00000000-0000-0000-0000-000000004560", response.last().id)
    }

    @Test
    fun `fetchUserProfiles() should parse 4xx response to SkillLabErrorResponseDto`() {
        // given
        mockWebServer.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """
                {
                  "error": {
                    "code": 400,
                    "title": "example-exception-title",
                    "message": "example-exception-message",
                    "detail": "example-exception-detail"
                  }
                }
                """.trimIndent()
            )
        )

        // then
        assertFailsWith(
            exceptionClass = ExternalSystemException::class,
            message = "example-exception-message",
            block = {
                // when
                service.fetchUserProfiles(pageable = Pageable.ofSize(10), updatedFrom = "2024-09-26T18:58:50.271Z")
            }
        )
    }

    private fun setUp() {
        service = SkillLabClient(
            http = SkillConfigurationSkillLab().skillLabApiClient(
                SkillLabApiClientConfiguration(
                    basePath = "http://localhost:$WEBSERVER_PORT",
                    apiKey = "dummy-api-key",
                    projectId = "dummy-project-id",
                    responseTimeoutInSeconds = 10
                )
            ),
            objectMapper = objectMapper
        )
    }

    private fun startWebserver() {
        mockWebServer = MockWebServer()
        mockWebServer.start(WEBSERVER_PORT)
    }

    private fun stopWebserver() {
        mockWebServer.shutdown()
    }
}
