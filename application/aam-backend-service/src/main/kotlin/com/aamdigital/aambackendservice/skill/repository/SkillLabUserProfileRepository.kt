package com.aamdigital.aambackendservice.skill.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.PagingAndSortingRepository


/**
 * JpaRepository and PagingAndSortingRepository interfaces provide all necessary query functions
 * Check Docs for further information: https://docs.spring.io/spring-data/jpa/reference/jpa/getting-started.html
 */
interface SkillLabUserProfileRepository : JpaRepository<SkillLabUserProfileEntity, Long>,
    PagingAndSortingRepository<SkillLabUserProfileEntity, Long> {
    fun existsByExternalIdentifier(externalIdentifier: String): Boolean
    fun findByExternalIdentifier(externalIdentifier: String): SkillLabUserProfileEntity
}
