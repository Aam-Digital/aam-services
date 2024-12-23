package com.aamdigital.aambackendservice.notification.repositiory

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.PagingAndSortingRepository
import java.util.*

interface UserDeviceRepository : PagingAndSortingRepository<UserDeviceEntity, Long>,
    CrudRepository<UserDeviceEntity, Long> {
    fun findByUserIdentifier(userIdentifier: String, pageable: Pageable): Page<UserDeviceEntity>
    fun findByDeviceToken(deviceToken: String): Optional<UserDeviceEntity>
    fun existsByDeviceToken(deviceToken: String): Boolean
    fun deleteByDeviceToken(deviceToken: String)
}
