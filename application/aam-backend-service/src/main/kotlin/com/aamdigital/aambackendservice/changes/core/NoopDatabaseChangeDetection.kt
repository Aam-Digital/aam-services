package com.aamdigital.aambackendservice.changes.core

import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

class NoopDatabaseChangeDetection : DatabaseChangeDetection {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun checkForChanges(): Mono<Unit> {
        logger.trace("[NoopDatabaseChangeDetection] start couchdb change detection...")
        return Mono.just(Unit)
            .map {
                logger.trace("[NoopDatabaseChangeDetection] ...completed couchdb change detection.")
            }
    }
}
