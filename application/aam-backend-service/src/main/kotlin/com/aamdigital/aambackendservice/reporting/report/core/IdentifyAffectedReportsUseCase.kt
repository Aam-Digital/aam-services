package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.changes.domain.DocumentChangeEvent
import com.aamdigital.aambackendservice.domain.DomainReference

interface IdentifyAffectedReportsUseCase {
    fun analyse(documentChangeEvent: DocumentChangeEvent): List<DomainReference>
}
