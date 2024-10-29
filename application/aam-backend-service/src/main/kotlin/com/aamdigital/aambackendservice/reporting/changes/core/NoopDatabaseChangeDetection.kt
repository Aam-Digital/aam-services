package com.aamdigital.aambackendservice.reporting.changes.core

class NoopDatabaseChangeDetection : DatabaseChangeDetection {
    override fun checkForChanges() {}
}
