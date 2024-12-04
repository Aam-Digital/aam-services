package com.aamdigital.aambackendservice.skill.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.PagingAndSortingRepository


/**
 * CrudRepository analyses the function name to create SQL queries
 * see: https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html
 */
interface SkillLabUserProfileRepository : JpaRepository<SkillLabUserProfileEntity, Long>,
    PagingAndSortingRepository<SkillLabUserProfileEntity, Long> {
    fun existsByExternalIdentifier(externalIdentifier: String): Boolean
    fun findByExternalIdentifier(externalIdentifier: String): SkillLabUserProfileEntity
}
