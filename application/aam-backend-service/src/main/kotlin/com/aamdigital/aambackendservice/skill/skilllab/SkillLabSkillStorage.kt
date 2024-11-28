package com.aamdigital.aambackendservice.skill.skilllab

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.skill.core.SkillStorage
import com.aamdigital.aambackendservice.skill.domain.EscoSkill
import org.springframework.data.domain.Pageable
import org.springframework.web.client.RestClient

class SkillLabSkillStorage(
    val http: RestClient
) : SkillStorage {
    override fun fetchSkill(externalIdentifier: DomainReference): EscoSkill {
        TODO("Not yet implemented")
    }

    override fun fetchSkills(pageable: Pageable): List<EscoSkill> {
        TODO("Not yet implemented")
    }
}
