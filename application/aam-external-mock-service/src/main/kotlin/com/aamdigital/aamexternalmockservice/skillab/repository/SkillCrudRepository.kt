package com.aamdigital.aamexternalmockservice.skillab.repository

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface SkillCrudRepository : CrudRepository<SkillEntity, Long> {

  @Query("SELECT s FROM SkillEntity s ORDER BY RANDOM() LIMIT 1")
  fun findRandom(): SkillEntity
}
