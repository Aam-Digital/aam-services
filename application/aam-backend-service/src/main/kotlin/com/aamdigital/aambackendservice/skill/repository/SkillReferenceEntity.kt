package com.aamdigital.aambackendservice.skill.repository

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class SkillReferenceEntity(
    @Column(unique = true)
    var externalIdentifier: String,

    @Column
    var escoUri: String,

    @Column
    var usage: String,
)
