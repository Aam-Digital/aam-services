package com.aamdigital.aamexternalmockservice.skillab.repository

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class LanguageEntity(
  @Column
  var language: String,

  @Column
  var proficiency: String,

  @Column(nullable = true)
  @JsonProperty("assessment_level")
  var assessmentLevel: String?,
)

enum class Proficiency {
  Beginner,
  Intermediate,
  Advanced,
  Fluent,
  Native,
}

