package com.aamdigital.aamexternalmockservice.skillab.repository

import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ProfilePagingRepository : PagingAndSortingRepository<ProfileEntity, UUID>

@Repository
interface ProfileCrudRepository : CrudRepository<ProfileEntity, UUID>
