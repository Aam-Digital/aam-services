package com.aamdigital.aamintegration.authentication.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.PagingAndSortingRepository
import java.util.*

interface AuthenticationSessionRepository : JpaRepository<AuthenticationSessionEntity, Long>,
    PagingAndSortingRepository<AuthenticationSessionEntity, Long> {
    fun findByExternalIdentifier(externalIdentifier: String): Optional<AuthenticationSessionEntity>
}
