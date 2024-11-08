package com.aamdigital.aamexternalmockservice.skillab.repository

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import java.util.*

@Entity
data class ProfileEntity(
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  var id: UUID = UUID.randomUUID(),

  @Column
  var city: String,

  @Column
  var country: String,

  @ElementCollection(fetch = FetchType.EAGER)
  var projects: MutableList<String>,

  @Column
  @JsonProperty("mobile_number")
  var mobileNumber: String,

  @Column
  @JsonProperty("full_name")
  var fullName: String,

  @Column
  var email: String,

  @Column
  @JsonProperty("street_and_house_number")
  var streetAndHouseNumber: String,

  @Column
  @JsonProperty("arrival_in_country")
  var arrivalInCountry: String,
 
  @Column
  var nationality: String,

  @Column
  var gender: String,

  @Column
  var birthday: String,

  @Column
  @JsonProperty("gender_custom")
  var genderCustom: String,

  @OneToMany(cascade = [CascadeType.ALL])
  var experiences: MutableList<ExperienceEntity>,

  @JsonProperty("updated_at")
  var updatedAt: String? = null,

  @ElementCollection(fetch = FetchType.EAGER)
  var languages: MutableList<LanguageEntity>,
)
