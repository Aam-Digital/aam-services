package com.aamdigital.aambackendservice.export.core

import com.aamdigital.aambackendservice.common.domain.DomainReference
import com.aamdigital.aambackendservice.common.error.ExternalSystemException
import com.aamdigital.aambackendservice.common.error.NetworkException
import com.aamdigital.aambackendservice.common.error.NotFoundException

interface TemplateStorage {
    @Throws(
        NotFoundException::class,
        ExternalSystemException::class,
        NetworkException::class
    )
    fun fetchTemplate(template: DomainReference): TemplateExport
}
