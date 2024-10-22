package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.domain.DomainReference
import com.aamdigital.aambackendservice.reporting.domain.event.DocumentChangeEvent

interface IdentifyAffectedReportsUseCase {
    fun analyse(documentChangeEvent: DocumentChangeEvent): List<DomainReference>
}
