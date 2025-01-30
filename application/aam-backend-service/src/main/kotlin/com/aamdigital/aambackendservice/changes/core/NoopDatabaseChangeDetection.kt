package com.aamdigital.aambackendservice.changes.core

class NoopDatabaseChangeDetection : DatabaseChangeDetection {
    override fun checkForChanges() {}
}
