package com.aamdigital.aambackendservice.export.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.error.ExternalSystemException
import com.aamdigital.aambackendservice.error.NetworkException
import com.aamdigital.aambackendservice.error.NotFoundException

interface TemplateStorage {
    @Throws(
        NotFoundException::class,
        ExternalSystemException::class,
        NetworkException::class
    )
    fun fetchTemplate(template: DomainReference): TemplateExport
}
