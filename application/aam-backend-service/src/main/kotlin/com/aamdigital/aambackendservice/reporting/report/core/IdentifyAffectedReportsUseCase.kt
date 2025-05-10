package com.aamdigital.aambackendservice.reporting.report.core

import com.aamdigital.aambackendservice.common.changes.domain.DocumentChangeEvent
import com.aamdigital.aambackendservice.common.domain.DomainReference

interface IdentifyAffectedReportsUseCase {
    fun analyse(documentChangeEvent: DocumentChangeEvent): List<DomainReference>
}
