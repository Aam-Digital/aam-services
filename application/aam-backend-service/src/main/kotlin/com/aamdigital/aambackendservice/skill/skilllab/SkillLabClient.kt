package com.aamdigital.aambackendservice.skill.skilllab

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.AamErrorCode
import com.aamdigital.aambackendservice.error.ExternalSystemException
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.Pageable
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import java.util.*

data class SkillLabErrorResponseDto(
    var error: SkillLabErrorDetailsDto
)

data class SkillLabErrorDetailsDto(
    var code: Int,
    var title: String = "",
    var message: String = "",
    var detail: String = "",
)

data class SkillLabProfilesResponseDto(
    val pagination: SkillLabPaginationDto,
    val results: List<SkillLabProfileIdDto>,
)

data class SkillLabProfileIdDto(
    val id: String,
)

data class SkillLabPaginationDto(
    @JsonProperty("current_page")
    val currentPage: Int,
    @JsonProperty("per_page")
    val perPage: Int,
    @JsonProperty("total_entries")
    val totalEntries: Int,
)

data class SkillLabSkillDto(
    var id: UUID = UUID.randomUUID(),
    @JsonProperty("external_id")
    val externalId: String,
    val choice: String,
)

data class SkillLabExperienceDto(
    @JsonProperty("experiences_skills")
    var experiencesSkills: MutableList<SkillLabSkillDto>,
)

data class SkillLabProfileResponseDto(
    val profile: SkillLabProfileDto,
)

data class SkillLabProfileDto(
    var id: String,
    var city: String?,
    var country: String?,
    var projects: List<String> = emptyList(),
    @JsonProperty("mobile_number")
    var mobileNumber: String?,
    @JsonProperty("full_name")
    var fullName: String?,
    var email: String?,
    @JsonProperty("street_and_house_number")
    var streetAndHouseNumber: String?,
    @JsonProperty("arrival_in_country")
    var arrivalInCountry: String?,
    var nationality: String?,
    var gender: String?,
    var birthday: String?,
    @JsonProperty("gender_custom")
    var genderCustom: String?,
    var experiences: List<SkillLabExperienceDto> = emptyList(),
    @JsonProperty("updated_at")
    var updatedAt: String?,
)

enum class SkillLabUserProfileStorageAamErrorCode : AamErrorCode {
    EMPTY_RESPONSE,
    NOT_FOUND,
    INTERNAL_SERVER_ERROR
}

class SkillLabClient(
    val http: RestClient,
    val objectMapper: ObjectMapper,
) {
    fun fetchUserProfile(externalIdentifier: DomainReference): SkillLabProfileResponseDto {
        return http.get()
            .uri("/profiles/${externalIdentifier.id}")
            .accept(MediaType.APPLICATION_JSON)
            .exchange { _, clientResponse ->
                val response = clientResponse.bodyTo(String::class.java) ?: throw ExternalSystemException(
                    message = "Empty or invalid response from server",
                    code = SkillLabUserProfileStorageAamErrorCode.EMPTY_RESPONSE
                )

                if (clientResponse.statusCode.is2xxSuccessful) {
                    val skillLabResponse =
                        objectMapper.readValue(response, SkillLabProfileResponseDto::class.java)

                    skillLabResponse
                } else {
                    val error = objectMapper.readValue(response, SkillLabErrorResponseDto::class.java)

                    throw ExternalSystemException(
                        message = error.error.message,
                        code = SkillLabUserProfileStorageAamErrorCode.INTERNAL_SERVER_ERROR
                    )

                }
            }
    }

    fun fetchUserProfiles(pageable: Pageable, updatedFrom: String?): List<DomainReference> {
        val uri = if (updatedFrom.isNullOrBlank()) {
            "/profiles?page=${pageable.pageNumber}&perPage=${pageable.pageSize}"
        } else {
            "/profiles?page=${pageable.pageNumber}&perPage=${pageable.pageSize}&updated_from=$updatedFrom"
        }

        return http.get()
            .uri(uri)
            .accept(MediaType.APPLICATION_JSON)
            .exchange { _, clientResponse ->
                val response = clientResponse.bodyTo(String::class.java) ?: throw ExternalSystemException(
                    message = "Empty or invalid response from server",
                    code = SkillLabUserProfileStorageAamErrorCode.EMPTY_RESPONSE
                )

                if (clientResponse.statusCode.is2xxSuccessful) {
                    val skillLabResponse = objectMapper.readValue(response, SkillLabProfilesResponseDto::class.java)
                    skillLabResponse.results.map { profileDto ->
                        DomainReference(
                            id = profileDto.id,
                        )
                    }
                } else {
                    val error = objectMapper.readValue(response, SkillLabErrorResponseDto::class.java)

                    throw ExternalSystemException(
                        message = error.error.message,
                        code = SkillLabUserProfileStorageAamErrorCode.INTERNAL_SERVER_ERROR
                    )

                }
            }
    }
}
