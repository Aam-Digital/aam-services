package com.aamdigital.aamexternalmockservice.skillab.repository

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToMany

@Entity
data class ExperienceEntity(
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE)
  var id: Long = 0,

  @Column
  var title: String,

  @Column
  var category: String,

  @Column
  @JsonProperty("start_date")
  var startDate: String,

  @Column
  @JsonProperty("end_date")
  var endDate: String,

  @Column
  var city: String,

  @Column
  var country: String,

  @Column
  @JsonProperty("duration_per_week")
  var durationPerWeek: String,

  @Column
  @JsonProperty("education_type")
  var educationType: String,

  @Column
  @JsonProperty("education_type_other")
  var educationTypeOther: String,

  @Column
  @JsonProperty("education_status")
  var educationStatus: String,

  @ManyToMany(fetch = FetchType.EAGER)
  @JsonProperty("experiences_skills")
  var experiencesSkills: MutableList<SkillEntity>,
)
