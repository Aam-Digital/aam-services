package com.aamdigital.aambackendservice.reporting.changes.core

import org.slf4j.LoggerFactory

class NoopDatabaseChangeDetection : DatabaseChangeDetection {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun checkForChanges() {
        logger.trace("[NoopDatabaseChangeDetection] checkForChanges() called.")
    }
}
