package com.aamdigital.aambackendservice.common.changes.core

class NoopDatabaseChangeDetection : DatabaseChangeDetection {
    override fun checkForChanges() {}
}
