package com.aamdigital.aambackendservice.changes.core

import reactor.core.publisher.Mono

interface DatabaseChangeDetection {
    fun checkForChanges(): Mono<Unit>
}
