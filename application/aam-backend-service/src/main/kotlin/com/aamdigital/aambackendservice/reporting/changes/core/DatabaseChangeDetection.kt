package com.aamdigital.aambackendservice.reporting.changes.core

import reactor.core.publisher.Mono

interface DatabaseChangeDetection {
    fun checkForChanges(): Mono<Unit>
}
