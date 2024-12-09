package com.aamdigital.aamexternalmockservice.skillab.repository

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.util.*

@Entity
data class SkillEntity(
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  var id: UUID = UUID.randomUUID(),

  @Column
  @JsonProperty("external_id")
  val externalId: String,

  @Column
  val choice: String,
)
