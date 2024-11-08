package com.aamdigital.aamexternalmockservice.skillab.controller

import com.aamdigital.aamexternalmockservice.skillab.repository.ExperienceEntity
import com.aamdigital.aamexternalmockservice.skillab.repository.LanguageEntity
import com.aamdigital.aamexternalmockservice.skillab.repository.Proficiency
import com.aamdigital.aamexternalmockservice.skillab.repository.ProfileCrudRepository
import com.aamdigital.aamexternalmockservice.skillab.repository.ProfileEntity
import com.aamdigital.aamexternalmockservice.skillab.repository.SkillCrudRepository
import com.aamdigital.aamexternalmockservice.skillab.repository.SkillEntity
import io.github.serpro69.kfaker.Faker
import io.github.serpro69.kfaker.fakerConfig
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.random.Random

@RestController
@RequestMapping("/skilllab/faker")
class FakerController(
  val profileCrudRepository: ProfileCrudRepository,
  val skillCrudRepository: SkillCrudRepository,
) {

  // Internal
  @PostMapping("/skills")
  fun addSkills(
    numberOfEntities: Int = 1000,
  ): ResponseEntity<Any> {
    val entities = mutableListOf<SkillEntity>()
    val faker = Faker()

    for (i in 1..numberOfEntities) {
      val entity = getSkill()
      entities.add(entity)
    }

    skillCrudRepository.saveAll(entities)
    return ResponseEntity.ok("Added entries to database.")
  }

  // Internal
  @PostMapping("/profiles")
  fun addProfiles(
    numberOfEntitiesPerCountry: Int = 10,
    addExperiences: Boolean = true,
  ): ResponseEntity<Any> {
    val countryCodes: List<String> = listOf("ee", "tr", "uk", "en", "de-de")
    val entities = mutableListOf<ProfileEntity>()

    countryCodes.forEach { countryCode ->
      val faker = Faker(fakerConfig { locale = countryCode })

      for (i in 1..numberOfEntitiesPerCountry) {
        val entity = getProfileEntity(faker)

        if (addExperiences) {
          for (i in 1..Random.nextInt(2, 5)) {
            entity.experiences.add(getExperience(faker))
          }

          for (i in 1..Random.nextInt(1, 3)) {
            entity.languages.add(
              LanguageEntity(
                language = faker.nation.language(),
                proficiency = Proficiency.entries.random().toString(),
                assessmentLevel = "${('A'..'C').random()}${Random.nextInt(1, 2)}"
              )
            )
          }
        }

        entities.add(entity)
      }
    }

    profileCrudRepository.saveAll(entities)
    return ResponseEntity.ok("Added entries to database.")
  }


  private fun getProfileEntity(
    faker: Faker
  ): ProfileEntity = ProfileEntity(
    city = faker.address.city(),
    country = faker.address.country(),
    projects = mutableListOf("IRADA"),
    mobileNumber = faker.phoneNumber.phoneNumber(),
    fullName = faker.name.name(),
    email = faker.internet.email(),
    streetAndHouseNumber = faker.address.streetAddress(),
    arrivalInCountry = faker.person.birthDate(Random.nextLong(1, 4))
      .format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
    nationality = faker.nation.nationality(),
    gender = faker.gender.binaryTypes(),
    birthday = faker.person.birthDate(Random.nextLong(12, 22))
      .format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
    genderCustom = "",
    experiences = mutableListOf(),
    updatedAt = faker.person.birthDate(Random.nextLong(1, 5))
      .atTime(
        Random.nextInt(0, 24),
        Random.nextInt(0, 60),
        Random.nextInt(0, 60)
      ).format(DateTimeFormatter.ISO_DATE_TIME),
    languages = mutableListOf()
  )

  private fun getSkill() = SkillEntity(
    externalId = "https://data.europa.test/esco/skill/${UUID.randomUUID()}",
    choice = "always"
  )

  private fun getExperience(
    faker: Faker
  ) = ExperienceEntity(
    title = faker.job.title(),
    category = "job",
    startDate = faker.person.birthDate(Random.nextLong(5, 10))
      .format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
    endDate = faker.person.birthDate(Random.nextLong(1, 5))
      .format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
    city = faker.address.city(),
    country = faker.address.country(),
    durationPerWeek = "${Random.nextInt(1, 6)}_days",
    educationType = faker.job.educationLevel(),
    educationTypeOther = "",
    educationStatus = "finished",
    experiencesSkills = mutableListOf<SkillEntity>().let { list ->
      for (i in 1..Random.nextInt(1, 5)) {
        list.add(skillCrudRepository.findRandom())
      }
      list
    }
  )

}
