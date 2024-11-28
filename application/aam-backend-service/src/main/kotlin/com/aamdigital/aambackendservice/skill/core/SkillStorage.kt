package com.aamdigital.aambackendservice.skill.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.skill.domain.EscoSkill
import org.springframework.data.domain.Pageable

interface SkillStorage {

    @Throws(AamException::class)
    fun fetchSkill(externalIdentifier: DomainReference): EscoSkill

    @Throws(AamException::class)
    fun fetchSkills(pageable: Pageable): List<EscoSkill>
}
